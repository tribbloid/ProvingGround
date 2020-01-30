package provingground.learning

import provingground._, HoTT._
import monix.eval._, monix.tail._
import monix.execution.Scheduler.Implicits.global
import shapeless._
import scala.concurrent.Future
import scala.collection.mutable.ArrayBuffer
import scala.collection.SeqView
import scala.util._
import scala.reflect.runtime.universe._

/* Code for mixed autonomous and interactive running.
 * We can interact by posting various objects.
 * There are also bots that post in reaction to other posts, in general using the results of queries.
 * The behaviour of a post is largely determined by its (scala) type.
 * The type `W` (for web) will handle posting and querying based on posts (this is implemented using typeclasses with `W`)
 */

/**
 * Typeclass for being able to post with content type P in W
 */
trait Postable[P, W, ID] {
  def post(content: P, web: W, pred: Set[ID]): Future[ID]
  val tag: TypeTag[P]
}

/**
  * Typeclass for being able to query W for type Q
  */
trait Queryable[U, W] {
  def get(web: W, predicate: U => Boolean): Future[U]
}

object Queryable{
  /**
    * a simple query, with result blocking and no predicate
    *
    * @param func the query function
    * @return typeclass implementation based on `func`
    */
  def simple[U, W](func: W => U) = new Queryable[U, W] {
    def get(web: W, predicate: U => Boolean): Future[U] = 
      Future(func(web))

  implicit def gatherQuery[P, W, ID](implicit ph: PostHistory[W, ID], pw: Postable[P, W, ID]) : Queryable[GatherPost[P] , W] = 
    new Queryable[GatherPost[P], W] {
     def get(web: W, predicate: GatherPost[P] => Boolean): Future[GatherPost[P]] = 
      Future(
        GatherPost(ph.allPosts(web).flatMap(_.getOpt[P]).toSet)
      )
    }
  }

  /**
    * querying for objects of a type, as a view
    *
    * @param pw postability of `P`, needed for history
    * @param ph post history of `P` from `W`
    * @return implementation of querying typeclass
    */
  implicit def allView[P, W, ID](implicit pw: Postable[P, W, ID], ph: PostHistory[W, ID]) : Queryable[SeqView[P, Seq[_]], W] = 
    new Queryable[SeqView[P, Seq[_]], W] {
      def get(web: W, predicate: SeqView[P, Seq[_]] => Boolean): Future[SeqView[P, Seq[_]]] = 
       Future{ ph.allPosts(web).filter(_.pw.tag == pw.tag).map{_.asInstanceOf[P]}}
    }
    

}
/**
 * Typeclass for being able to query W for a vector of elements of type Q at an index
 */ 
trait LocalQueryable[U, W, ID] {
  def getAt(web: W, id: ID, predicate: U => Boolean): Future[Vector[U]]
}

/** 
  * Looking up an answer to a query for `U` from posts of type `P`
  * meant for querying for the latest post etc.
  *
  * @param func transformation of the content of the post
  * @param pw postability
  */
case class AnswerFromPost[P, U, W, ID](func: P => U)(
  implicit pw: Postable[P, W, ID]
) {
def fromPost[Q](data: PostData[Q, W, ID]) : Option[U] =
  if (pw.tag == data.pw.tag) Some(func(data.content.asInstanceOf[P])) else None
}

/**
  * Queries answered by taking the latest out of possibly many choices of posts to be looked up and transformed.
  *
  * @param answers various ways in which the query can be answered
  * @param h the history
  */
case class LatestAnswer[Q, W, ID](
  answers: Vector[AnswerFromPost[_, Q, W, ID]]
)(implicit h: PostHistory[W, ID])
  extends LocalQueryable[Q, W, ID] {
    def getAt(web: W, id: ID, predicate: Q => Boolean): Future[Vector[Q]] = Future{
      def answer: PostData[_, W, ID] => Option[Q] = 
        (pd) => answers.flatMap(ans => ans.fromPost(pd)).filter(predicate).headOption
      h.latestAnswers(web, id, answer).toVector


    }

    def ||[P](ans: P => Q)(implicit pw: Postable[P, W, ID]) = 
      LatestAnswer(
        answers :+ AnswerFromPost[P, Q, W, ID](ans)
      )
  }


trait FallBackLookups{
  /**
    * default implementation of lookup for stuff that can be posted given a post-history implementation,
    * where we look fot the latest post.
    *
    * @param qw postability of `Q`
    * @param ph post history
    * @return implementation of query lookup
    */
  implicit   def lookupLatest[Q, W, ID](implicit qw: Postable[Q, W, ID], ph: PostHistory[W, ID]) : LocalQueryable[Q, W, ID] = 
  LatestAnswer(Vector(AnswerFromPost[Q, Q, W, ID](identity)))

  /**
    * Lookup for the wrapped type `SomePost`, which returns all posts independent of relative position in history.
    *
    * @param pw postability of `P`
    * @param ph post history
    * @return implementation of query lookup
    */
  implicit def somePostQuery[P, W, ID](implicit pw: Postable[P, W, ID], ph: PostHistory[W, ID]) : LocalQueryable[SomePost[P], W, ID] = new LocalQueryable[SomePost[P], W, ID] {
   def getAt(web: W, id: ID, predicate: SomePost[P] => Boolean): Future[Vector[SomePost[P]]] =
     Future{ph.allPosts(web).filter(_.pw.tag == pw.tag).map{post => SomePost(post.content.asInstanceOf[P])}.filter(predicate).toVector
  } 
}
     
}

object LocalQueryable extends FallBackLookups{
  /**
    * Query for an object
    *
    * @param web the web which we query
    * @param predicate property the object must satisfy
    * @param q queryability
    * @return future result of type`Q`
    */
  def query[Q, W](web: W, predicate: Q => Boolean)(implicit q: Queryable[Q, W]):  Future[Q] = q.get(web, predicate)

  /**
    * Query for an object at a position, for example the latest before that position
    *
    * @param web the web which we query
    * @param id the position where we query
    * @param predicate property the object must satisfy
    * @param q queryability
    * @return future result of type`Q`
    */
  def queryAt[Q, W, ID](web: W, id: ID, predicate: Q => Boolean)(implicit q: LocalQueryable[Q, W, ID]) =
    q.getAt(web, id, predicate)

  /**
    * use a global query for local querying, ignoring position
    * 
    * @param q global queryability
    * @return local query implementation
    */
  implicit def localize[U, W, ID](
      implicit q: Queryable[U, W]
  ): LocalQueryable[U, W, ID] =
    new LocalQueryable[U, W, ID] {
      def getAt(web: W, id: ID, predicate: U => Boolean): Future[Vector[U]] = q.get(web, predicate).map(Vector(_))
    }

  def lookupAnswer[P, W, ID](
    implicit pw: Postable[P, W, ID]
  ): AnswerFromPost[P, P, W, ID] = AnswerFromPost(identity(_))

  /**
    * query for `Some[A]` using query for `A` by looking up history. Meant to be starting point for several options.
    *
    * @param qw query for `A`
    * @param ph post history
    * @return queryable for `Some[A]`
    */
  def answerAsSome[Q, W, ID](implicit qw: Postable[Q, W, ID], ph: PostHistory[W, ID]) : LatestAnswer[Some[Q],W,ID] = 
    LatestAnswer(Vector(AnswerFromPost[Q, Some[Q], W, ID](Some(_))))


  /**
   * Look up an answer in the history of a post, assuming an implicit history provider
   */ 
  case class RecentAnswer[Q, W, ID](
      answers: Seq[AnswerFromPost[_, Q, W, ID]]
  )(implicit h: PostHistory[W, ID])
      extends LocalQueryable[Q, W, ID] {
    def getAt(web: W, id: ID, predicate: Q => Boolean) =
      Future {
        val stream = for {
          pd  <- h.history(web, id)
          ans <- answers
          res <- ans.fromPost(pd)
        } yield res
        stream.toVector.take(1)
      }
  }


  
  /**
    * query a conjunction of two queryable types
    * 
    * @param qu queryability of the first
    * @param qv queryability of the second
    * @return implemented queryability putting these together
    */
  implicit def hconsQueryable[U, V <: HList, W, ID](
      implicit qu: LocalQueryable[U, W, ID],
      qv: LocalQueryable[V, W, ID]
  ) : LocalQueryable[U :: V, W, ID] = new LocalQueryable[U:: V, W, ID] {
      def getAt(web: W, id: ID, predicate: U :: V => Boolean): Future[Vector[U :: V]] = 
        (for {
            head <- qu.getAt(web, id, (_) => true)
            tail <- qv.getAt(web, id, (_) => true)
        } yield head.flatMap(x => tail.map(y => x :: y))
        ).map(_.filter{case pair => predicate(pair)})
          
  }

  /**
  * Querying for HNil
  *
  * @return trivial query response
  */
  implicit def hNilQueryable[W, ID] : LocalQueryable[HNil, W, ID] = new LocalQueryable[HNil, W, ID] {
      def getAt(web: W, id: ID, predicate: HNil => Boolean): Future[Vector[HNil]] = Future(Vector(HNil))
  }

  /**
  * Querying for Unit
  *
  * @return trivial query response
  */
  implicit def unitQueryable[W, ID] : LocalQueryable[Unit, W, ID] = new LocalQueryable[Unit, W, ID] {
    def getAt(web: W, id: ID, predicate: Unit => Boolean): Future[Vector[Unit]] = Future(Vector(()))
}
}
/**
 * Data for a post, including the implicit saying it is postable
 * @param content the content of the post
 * @param id the index of the post, returned after posting
 */ 
case class PostData[P, W, ID](content: P, id: ID)(
    implicit val pw: Postable[P, W, ID]
){
  def getOpt[Q](implicit qw: Postable[Q, W, ID]) : Option[Q] = if (pw.tag == qw.tag) Some(content.asInstanceOf[Q]) else None 
}

trait PostHistory[W, ID] {
  // the post itself and all its predecessors
  def findPost(web: W, index: ID) :  Option[(PostData[_, W, ID], Set[ID])]

  // all posts as a view
  def allPosts(web: W): SeqView[PostData[_, W, ID], Seq[_]]

  def history(web: W, id: ID): Stream[PostData[_, W, ID]] = {
    val next : ((Set[PostData[_, W, ID]], Set[ID])) =>  (Set[PostData[_, W, ID]], Set[ID])   = {case (d, indices) =>
      val pairs = indices.map(findPost(web, _)).flatten
      (pairs.map(_._1), pairs.flatMap(_._2))
    }
    def stream : Stream[(Set[PostData[_, W, ID]], Set[ID])] = 
      ((Set.empty[PostData[_, W, (ID)]], Set(id))) #:: stream.map(next)
    stream.flatMap(_._1)
  }

  def redirects(web: W) : Map[ID, Set[ID]] 

  /**
    * latest answers in the query using history
    *
    * @param web the web with all posts
    * @param id where we query
    * @param answer transformation of posts
    * @return
    */
  def latestAnswers[Q](web: W, id: ID, answer: PostData[_, W, ID] => Option[Q]) : Set[Q] = 
    findPost(web, id).map{
      case (pd, preds) => 
        answer(pd).map(Set(_)).getOrElse(
          preds.flatMap(pid => latestAnswers(web, pid, answer))
          )
    }.orElse(redirects(web).get(id).map(ids => ids.flatMap(rid => latestAnswers(web, rid, answer)))
    ).getOrElse(Set())
}

object PostHistory{
  case class Empty[W, ID]() extends PostHistory[W, ID]{
    def findPost(web: W, index: ID): Option[(PostData[_, W, ID], Set[ID])] = None
    def allPosts(web: W): SeqView[PostData[_, W, ID],Seq[_]] = Seq().view
    def redirects(web: W): Map[ID,Set[ID]] = Map()
  }

  case class Or[W, ID](first: PostHistory[W, ID], second: PostHistory[W, ID]) extends PostHistory[W, ID]{
    def findPost(web: W, index: ID): Option[(PostData[_, W, ID], Set[ID])] = first.findPost(web, index).orElse(second.findPost(web, index))
    def allPosts(web: W): SeqView[PostData[_, W, ID],Seq[_]] = first.allPosts(web) ++ second.allPosts(web)
    def redirects(web: W): Map[ID,Set[ID]] = first.redirects(web) ++ second.redirects(web)
  }

  def get[W, B, ID](buffer: W => B)(implicit hg : HistoryGetter[W, B, ID]) : PostHistory[W, ID] = hg.getHistory(buffer)
}

trait HistoryGetter[W, B, ID]{
  def getHistory(buffer: W => B) : PostHistory[W, ID]
}

object HistoryGetter{
  def get[W, B, ID](buffer: W => B)(implicit hg: HistoryGetter[W, B, ID]) : PostHistory[W,ID] = hg.getHistory(buffer)

  implicit def nilGetter[W, ID] : HistoryGetter[W, HNil, ID] = 
    new HistoryGetter[W, HNil, ID] {
      def getHistory(buffer: W => HNil): PostHistory[W,ID] = PostHistory.Empty[W, ID]
    }
  
  implicit def consGetter[W, B1, B2 <: HList, ID](implicit g1: HistoryGetter[W, B1, ID], g2: HistoryGetter[W, B2, ID]) : HistoryGetter[W, B1 :: B2, ID] =
    new HistoryGetter[W, B1 :: B2, ID] {
      def getHistory(buffer: W => B1 :: B2): PostHistory[W,ID] = 
      PostHistory.Or(
        g1.getHistory((w: W) => buffer(w).head),
        g2.getHistory((w: W) => buffer(w).tail)
      )
    }

  implicit def bufferGetter[P, W, ID](implicit pw: Postable[P, W, ID]) : HistoryGetter[W, PostBuffer[P, ID], ID] = 
    new HistoryGetter[W, PostBuffer[P, ID], ID] {
      def getHistory(buffer: W => PostBuffer[P,ID]): PostHistory[W,ID] = 
        new PostHistory[W, ID] {
          def findPost(web: W, index: ID): Option[(PostData[_, W, ID], Set[ID])] = buffer(web).find(index)
          def allPosts(web: W): SeqView[PostData[_, W, ID],Seq[_]] = buffer(web).bufferData.view
          def redirects(web: W): Map[ID,Set[ID]] = Map()
        }
    }

  implicit def erasableBufferGetter[P, W, ID](implicit pw: Postable[P, W, ID]) : HistoryGetter[W, ErasablePostBuffer[P, ID], ID] = 
    new HistoryGetter[W, ErasablePostBuffer[P, ID], ID] {
      def getHistory(buffer: W => ErasablePostBuffer[P,ID]): PostHistory[W,ID] = 
        new PostHistory[W, ID] {
          def findPost(web: W, index: ID): Option[(PostData[_, W, ID], Set[ID])] = buffer(web).find(index)
          def allPosts(web: W): SeqView[PostData[_, W, ID],Seq[_]] = buffer(web).bufferData.view
          def redirects(web: W): Map[ID,Set[ID]] = buffer(web).redirects
        }
    }
}

/**
  * Wrapper to query for all posts, even after the query position or in a different thread
  *
  * @param content the wrapped content
  */
case class SomePost[P](content: P)

case class GatherPost[P](contents: Set[P])

/**
 * Response to a post, generating one or more posts or just a callback;
 * this exists mainly for nicer type collections.
 */ 
sealed trait PostResponse[W, ID]{
  type PostType

  def postFuture(web: W, content: PostType, id: ID): Future[Vector[PostData[_, W, ID]]]
}

object PostResponse {
  /**
   * Casting to a typed post response if the Postables match
   */ 
  def typedResponseOpt[Q, W, ID](post: Q, response: PostResponse[W, ID])(
      implicit qp: Postable[Q, W, ID]
  ): Option[TypedPostResponse[Q, W, ID]] = response match {
    case r: TypedPostResponse[p, W, ID] =>
      if (qp == r.pw) Some(r.asInstanceOf[TypedPostResponse[Q, W, ID]])
      else None
  }

  /**
   * Given a post response and a post, 
   * if types match (as evidenced by implicits) then the the response is run and the 
   * new posts wrapped as PostData are returned (the wrapping allows implicits to be passed on).
   */ 
  def postResponseFuture[Q, W, ID](
      web: W,
      post: Q,
      id: ID,
      response: PostResponse[W, ID]
  )(
      implicit qp: Postable[Q, W, ID]
  ): Future[Vector[PostData[_, W, ID]]] = {
    val chainOpt = typedResponseOpt(post, response)(qp)
      .map(tr => tr.postFuture(web, post, id))
      .toVector
    val flip = Future.sequence(chainOpt)
    flip.map(_.flatten)
  }

}

/**
  * A simple session to post stuff, call responses and if these responses generate posts, recursively call itself for them.
  *
  * @param web the web where the posts are stored
  * @param responses the bots responding to posts
  * @param logs side-effect responses
  */ 
class SimpleSession[W, ID](
    val web: W,
    var responses: Vector[PostResponse[W, ID]],
    logs: Vector[PostData[_, W, ID] => Future[Unit]]
) {
  /**
    * recursively posting and running (as side-effects) offspring tasks, this posts the head but 
    * bots will be called with a different method that does not post the head, to avoid duplication
    *
    * @param content the post content
    * @param preds the predecessor posts
    * @param pw postability
    * @return the data of the post as a future
    */ 
  def postFuture[P](content: P, preds: Set[ID])(implicit pw: Postable[P, W, ID]): Future[PostData[P, W, ID]] = {
    val postIdFuture = pw.post(content, web, preds)
    postIdFuture.foreach { postID => // posting done, the id is now the predecessor for further posts
      tailPostFuture(content, postID)
    }
    postIdFuture.map{id => 
      val data = PostData(content, id)
      logs.foreach{fn => fn(data).foreach(_ => ())}
      data}
  }

  /**
    * the recursive step for posting, the given content is not posted, only the responses are.
    *
    * @param content the head content, not post
    * @param postID the ID of the head, to be used as predecessor for the other posts
    * @param pw postability
    */
  def tailPostFuture[P](content: P, postID: ID)(implicit pw: Postable[P, W, ID]) : Unit = {
    responses.foreach(
      response =>
        PostResponse.postResponseFuture(web, content, postID, response).map {
          v =>
            v.map {
              case pd: PostData[q, W, ID] => 
               logs.foreach{fn => fn(pd).foreach(_ => ())}
                tailPostFuture(pd.content, pd.id)(pd.pw)
            }
        }
    ) 
  }

  /**
    * a query from the web
    *
    * @param predicate condition to be satisfied
    * @param q queribility
    * @return response to query as a future
    */
  def query[Q](predicate: Q => Boolean = (_: Q) => true)(implicit q: Queryable[Q, W]) : Future[Q] = q.get(web, predicate)

  /**
    * a query from the web at a position
    *
    * @param id the postion
    * @param predicate condition to be satisfied
    * @param q queribility
    * @return response to query as a future
    */
  def queryAt[Q](id: ID, predicate: Q => Boolean= (_: Q) => true)(implicit q: LocalQueryable[Q, W, ID]) : Future[Vector[Q]] =
    q.getAt(web, id, predicate)
}

/**
  * Post response with type `P` of post as a type parameter
  *
  * @param pw postability of `P`
  */
sealed abstract class TypedPostResponse[P, W, ID](
    implicit val pw: Postable[P, W, ID]
) extends PostResponse[W, ID] {
  type PostType = P

  // the posts in response to a given one, may be none
  def postFuture(web: W, content: P, id: ID): Future[Vector[PostData[_, W, ID]]]
}

object TypedPostResponse {
  /**
    * Callback executed on a post of type P;
    * the returned task gives an empty vector, but running it executes the callback,
    * in fact, a callback is executed for each value of the auxiliary queryable
    *
    * @param update the callback, may also update the web as a side-effect
    * @param predicate condition on post to trigger callbask
    * @param pw postability 
    * @param lv queryability of parameters on which the callback depends
    */ 
  case class Callback[P, W, V, ID](update: W => V => P => Future[Unit], predicate: V => Boolean = (_ : V) => true)(
      implicit pw: Postable[P, W, ID],
      lv: LocalQueryable[V, W, ID]
  ) extends TypedPostResponse[P, W, ID] {

    def postFuture(
        web: W,
        content: P,
        id: ID
    ): Future[Vector[PostData[_, W, ID]]] = {
      val auxFuture = lv.getAt(web, id, predicate)
      val task = auxFuture.flatMap { auxs =>
        Future.sequence(auxs.map(aux => update(web)(aux)(content))).map(_ => Vector.empty[PostData[_, W, ID]])
      }
      task
    }
  }

  object Callback{
    def simple[P, W, ID](func: W => P => Unit)(
      implicit pw: Postable[P, W, ID]) = 
      Callback[P, W, Unit, ID]{
        (web: W) => (_: Unit) => (p: P) => Future(func(web)(p))
    }
  }

  /**
    * Bot responding to a post returning a vector of posts, 
    * one for each value of the auxiliary queryable (in simple cases a singleton is returned)
    *
    * @param response the action of the bot
    * @param predicate the condition the post must satisfy to trigger the bot
    * @param pw postability of the post type
    * @param qw postability of the response post type
    * @param lv queryability of the other arguments
    */ 
  case class MicroBot[P, Q, W, V, ID](response: V => P => Future[Q], predicate: V => Boolean = (_ : V) => true)(
      implicit pw: Postable[P, W, ID],
      qw: Postable[Q, W, ID],
      lv: LocalQueryable[V, W, ID]
  ) extends TypedPostResponse[P, W, ID] {

    def postFuture(
        web: W,
        content: P,
        id: ID
    ): Future[Vector[PostData[_, W, ID]]] = {
      val auxFuture = lv.getAt(web, id, predicate) // auxiliary data from queries
      val taskNest =
        auxFuture.map{
          (auxs => 
            auxs.map{
              aux => 
                val newPostFuture = response(aux)(content)
                newPostFuture.flatMap{newPost => 
                  val idNewFuture = qw.post(newPost, web, Set(id))
                  idNewFuture.map(idNew => PostData(newPost, idNew))}
            })
        }
      val task = taskNest.flatMap(st => Future.sequence(st))
      task
    }
  }

  /**
    * Probabaly not needed, need to just post pairs
    *
    * @param response
    * @param predicate
    * @param pw
    * @param q1w
    * @param q2w
    * @param lv
    */
  case class PairBot[P, Q1, Q2<: HList, W, V, ID](response: V => P => Future[Q1 :: Q2], predicate: V => Boolean = (_ : V) => true)(
      implicit pw: Postable[P, W, ID],
      q1w: Postable[Q1, W, ID],
      q2w: Postable[Q2, W, ID],
      lv: LocalQueryable[V, W, ID]
  ) extends TypedPostResponse[P, W, ID] {

    def postFuture(
        web: W,
        content: P,
        id: ID
    ): Future[Vector[PostData[_, W, ID]]] = {
      val auxFuture = lv.getAt(web, id, predicate) // auxiliary data from queries
      val taskNest =
        auxFuture.map{
          (auxs => 
            auxs.map{
              aux => 
                val newPostFuture = response(aux)(content)
                newPostFuture.flatMap{case newPost :: np2  => 
                  val idNewFuture = q1w.post(newPost, web, Set(id))
                  idNewFuture.flatMap{idNew => 
                    val id2Fut = q2w.post(np2, web, Set(idNew))
                    id2Fut.map(id2 => PostData(np2, id2))}
                  }
            })
        }
      val task = taskNest.flatMap(st => Future.sequence(st))
      task
    }
  }

  object MicroBot{
    def simple[P, Q, W, ID](func: P => Q)(implicit pw: Postable[P, W, ID],
    qw: Postable[Q, W, ID]) = MicroBot[P, Q, W, Unit, ID](
      (_ : Unit) => (p: P) => Future(func(p))
    )
  }
}

/**
  * Bot responding to a post returning a vector of posts for  
  * each value of the auxiliary queryable - so even if the auxiliary has a single response, many posts are made 
  *
  * @param responses the responses of the bot
  * @param predicate the condition the post must satisfy to trigger the bot
  * @param pw postability of the post type
  * @param qw postability of the response post type
  * @param lv queryability of the other arguments
  */
case class MiniBot[P, Q, W, V, ID](responses: V => P => Future[Vector[Q]], predicate: V => Boolean)(
      implicit pw: Postable[P, W, ID],
      qw: Postable[Q, W, ID],
      lv: LocalQueryable[V, W, ID]
  ) extends TypedPostResponse[P, W, ID] {

    def postFuture(
        web: W,
        content: P,
        id: ID
    ): Future[Vector[PostData[_, W, ID]]] = {
      val auxFuture = lv.getAt(web, id, predicate) // auxiliary data from queries
      val taskNest =
        auxFuture.map{
          (auxs => 
            auxs.map{
              aux => 
                val newPostsFuture = responses(aux)(content)
                newPostsFuture.flatMap{newPosts => // extra nesting for multiple posts
                  Future.sequence(newPosts.map{newPost =>
                    val idNewFuture = qw.post(newPost, web, Set(id))
                    idNewFuture.map(idNew => PostData(newPost, idNew))}
                  )}
            })
        }
      val task = taskNest.flatMap(st => Future.sequence(st).map(_.flatten))
      task
    }
}

/**
  * Allows posting any content, typically just returns an ID to be used by something else.
  */
trait GlobalPost[P, ID] {
  def postGlobal(content: P): Future[ID]
}

/**
  * A buffer for storing posts, extending `GlobalPost` which supplies an ID
  */
trait PostBuffer[P, ID] extends GlobalPost[P, ID] { self =>
  val buffer: ArrayBuffer[(P, ID, Set[ID])] = ArrayBuffer()

  def post(content: P, prev: Set[ID]): Future[ID] = {
    val idT = postGlobal(content)
    idT.map { id =>
      buffer += ((content, id, prev))
      id
    }
  }

  def find[W](index: ID)(implicit pw:  Postable[P, W, ID]) :  Option[(PostData[P,W,ID], Set[ID])] = buffer.find(_._2 == index).map{
    case (p, _, preds) => (PostData[P, W, ID](p, index), preds)
  }

  def bufferData[W](implicit pw: Postable[P, W, ID]) : Vector[PostData[_, W, ID]] =
    buffer.map{case (p, id, _) => PostData(p, id)}.toVector

  def bufferFullData[W](implicit pw: Postable[P, W, ID]) : Vector[(PostData[P,W,ID], ID, Set[ID])] =
    buffer.map{case (p, id, preds) => (PostData(p, id), id, preds)}.toVector

}


case class WebBuffer[P, ID](buffer: PostBuffer[P, ID])(
      implicit pw: Postable[P, HoTTPost, ID]
  ) {
    def getPost(id: ID): Option[(PostData[_, HoTTPost, ID], Set[ID])] =
      buffer.find(id)

    def data: Vector[PostData[_, HoTTPost, ID]] = buffer.bufferData

    def fullData: Vector[(PostData[_, HoTTPost, ID], ID, Set[ID])] =
      buffer.bufferFullData
  }

object ErasablePostBuffer{
  def bufferPost[P : TypeTag, W, ID](buffer: W => ErasablePostBuffer[P, ID]) : Postable[P, W, ID] = {
    def postFunc(p: P, web: W, ids: Set[ID]): Future[ID] =
      buffer(web).post(p, ids)
    Postable(postFunc)
  }

  def build[P, ID](implicit gp: GlobalID[ID]) : ErasablePostBuffer[P, ID] = new ErasablePostBuffer[P, ID] {
    def postGlobal(content: P): Future[ID] = gp.postGlobal(content)
  }

}  

trait ErasablePostBuffer[P, ID] extends GlobalPost[P, ID] { self =>
  val buffer: ArrayBuffer[(Option[P], ID, Set[ID])] = ArrayBuffer()

  def redirects :  Map[ID,Set[ID]] = buffer.collect{case (None, id, preds) => id -> preds}.toMap

  def post(content: P, prev: Set[ID]): Future[ID] = {
    val idT = postGlobal(content)
    idT.map { id =>
      buffer += ((Some(content), id, prev))
      id
    }
  }

  def find[W](index: ID)(implicit pw:  Postable[P, W, ID]) :  Option[(PostData[P,W,ID], Set[ID])] = buffer.find(_._2 == index).flatMap{
    case (pOpt, _, preds) => pOpt.map(p => (PostData[P, W, ID](p, index), preds))
  }

  def skipDeletedStep(index: ID) : Option[Set[ID]] = buffer.find(pd => pd._1.isEmpty && pd._2 == index).map(_._3)

  def bufferData[W](implicit pw: Postable[P, W, ID]) : Vector[PostData[_, W, ID]] =
    buffer.flatMap{case (pOpt, id, _) =>   pOpt.map(p => PostData[P, W, ID](p, id))}.toVector

  def bufferFullData[W](implicit pw: Postable[P, W, ID]) : Vector[(PostData[P,W,ID], ID, Set[ID])] =
    buffer.flatMap{case (pOpt, id, preds) => pOpt.map(p => (PostData[P, W, ID](p, id), id, preds))}.toVector

}

case class ErasableWebBuffer[P, ID](buffer: ErasablePostBuffer[P, ID])(
      implicit pw: Postable[P, HoTTPost, ID]
  ) {
    def getPost(id: ID): Option[(PostData[_, HoTTPost, ID], Set[ID])] =
      buffer.find(id)

    def data: Vector[PostData[_, HoTTPost, ID]] = buffer.bufferData

    def fullData: Vector[(PostData[_, HoTTPost, ID], ID, Set[ID])] =
      buffer.bufferFullData
  }

object PostBuffer {
/**
  * creating a post buffer
  *
  * @param globalPost the supplier of the ID
  * @return buffer storing posts
  */  
  def apply[P, ID](globalPost: => (P => Future[ID])) : PostBuffer[P, ID] = new PostBuffer[P, ID] {
    def postGlobal(content: P): Future[ID] = globalPost(content)
  }

  def build[P, ID](implicit gp: GlobalID[ID]) : PostBuffer[P, ID] = new PostBuffer[P, ID] {
    def postGlobal(content: P): Future[ID] = gp.postGlobal(content)
  }

  /**
    * content from buffer
    *
    * @param pb the buffer
    * @param id ID
    * @return content optionally
    */
  def get[P, ID](pb: PostBuffer[P, ID], id: ID): Option[P] =
    pb.buffer.find(_._2 == id).map(_._1)

    /**
      * immediate predecessor posts in buffer
      *
      * @param pb the buffer
      * @param id ID
      * @return set of IDs of immediate predecessors
      */
  def previous[P, ID](pb: PostBuffer[P, ID], id: ID) : Set[ID] = {
    val withId =  pb.buffer.filter(_._2 == id).toSet
    withId.flatMap(_._3)
  }

  /**
    * postability using a buffer, the main way posting is done
    *
    * @param buffer the buffer to which to post as a function of the web
    * @return postability
    */
  def bufferPost[P : TypeTag, W, ID](buffer: W => PostBuffer[P, ID]) : Postable[P, W, ID] = {
    def postFunc(p: P, web: W, ids: Set[ID]): Future[ID] =
      buffer(web).post(p, ids)
    Postable(postFunc)
  }
}

/**
 * typeclass for building HLists of postables based on HLists of buffers, but formally just returns object of type `P` 
 */ 
trait BuildPostable[W, B, P]{
  def postable(buffer: W => B) : P
}

object BuildPostable{
  def get[W, B, P](buffer: W => B)(implicit bp: BuildPostable[W, B, P]) : P = bp.postable(buffer)

  implicit def hnilTriv[W] : BuildPostable[W, HNil, HNil] = 
    new BuildPostable[W, HNil, HNil] {
      def postable(buffer: W => HNil): HNil = HNil
    }
  
  implicit def bufferCons[W, P: TypeTag, ID, Bt <: HList, Pt <: HList](
    implicit tailBuilder: BuildPostable[W, Bt, Pt]) : BuildPostable[W, PostBuffer[P, ID] :: Bt, Postable[P, W, ID] :: Pt] = 
      new BuildPostable[W, PostBuffer[P, ID] :: Bt, Postable[P, W, ID] :: Pt]{
        def postable(buffer: W => PostBuffer[P,ID] :: Bt): Postable[P,W,ID] :: Pt = 
          PostBuffer.bufferPost((web: W) => buffer(web).head) :: tailBuilder.postable((web: W) => buffer(web).tail)
      }

  implicit def erasablebufferCons[W, P: TypeTag, ID, Bt <: HList, Pt <: HList](
    implicit tailBuilder: BuildPostable[W, Bt, Pt]) : BuildPostable[W, ErasablePostBuffer[P, ID] :: Bt, Postable[P, W, ID] :: Pt] = 
      new BuildPostable[W, ErasablePostBuffer[P, ID] :: Bt, Postable[P, W, ID] :: Pt]{
        def postable(buffer: W => ErasablePostBuffer[P,ID] :: Bt): Postable[P,W,ID] :: Pt = 
          ErasablePostBuffer.bufferPost((web: W) => buffer(web).head) :: tailBuilder.postable((web: W) => buffer(web).tail)
      }
}

object Postable {
  /**
    * Making a postable
    *
    * @param postFunc the posting function
    * @return a Postable
    */
  def apply[P : TypeTag, W, ID](postFunc: (P, W, Set[ID]) => Future[ID]) : Postable[P, W, ID] = 
    Impl(postFunc)

    /**
      * A concrete implementation of postable
      *
      * @param postFunc the posting function
      */
  case class Impl[P, W, ID](postFunc: (P, W, Set[ID]) => Future[ID])(implicit val tag: TypeTag[P]) extends Postable[P, W, ID] {
        def post(content: P, web: W, pred: Set[ID]): Future[ID] = 
            postFunc(content, web, pred)
    }

    /**
      * post and return post data
      *
      * @param content content to be posted
      * @param web web where we post
      * @param pred predecessors of the post
      * @param pw postability of the type
      * @return PostData as a future
      */
  def postFuture[P, W, ID](content: P, web: W, pred: Set[ID])(implicit pw: Postable[P, W, ID]) : Future[PostData[P, W, ID]] = 
    pw.post(content, web, pred).map{id => PostData(content, id)} 

    /**
      * post in a buffer
      *
      * @return postability
      */
  implicit def bufferPostable[P: TypeTag, ID, W <: PostBuffer[P, ID]]
      : Postable[P, W, ID] =
     {
      def post(content: P, web: W, pred: Set[ID]): Future[ID] =
        web.post(content, pred)
      Impl(post)
    }
  
    /**
      * post an Option[P], posting a Unit in case of `None`
      *
      * @param pw postability of `P`
      * @param uw postability of a Unit
      * @return postability of `Option[P]`
      */
  implicit def optionPostable[P : TypeTag, W, ID](implicit pw: Postable[P, W, ID], uw: Postable[Unit, W, ID]) : Postable[Option[P], W, ID] = 
    {
      def post(content: Option[P], web: W, pred: Set[ID]): Future[ID] = 
        content.fold(uw.post((), web, pred))(c => pw.post(c, web, pred))
        Impl(post)
    }

  /**
    * post a `Try[P]` by posting `P` or an error 
    *
    * @param pw postability of `P`
    * @param ew postability of a `Throwable`
    * @return postability of `Try[P]`
    */
  implicit def tryPostable[P : TypeTag, W, ID](implicit pw: Postable[P, W, ID], ew: Postable[Throwable, W, ID]) : Postable[util.Try[P], W, ID] = 
    {
      def post(content: util.Try[P], web: W, pred: Set[ID]): Future[ID] =
        content.fold(err => ew.post(err, web, pred), c => pw.post(c, web, pred))

      Impl(post)
    }

  implicit def eitherPostable[P : TypeTag, Q: TypeTag, W, ID](implicit pw: Postable[P, W, ID], qw: Postable[Q, W, ID]) : Postable[Either[P, Q], W, ID] =
    {
     def post(content: Either[P, Q], web: W, pred: Set[ID]): Future[ID] =
      content.fold(
        fa => pw.post(fa, web, pred), 
        fb => qw.post(fb, web, pred))
    Impl(post)
    }

  implicit def pairPost[P: TypeTag, Q <: HList : TypeTag, W, ID](implicit pw: Postable[P, W, ID], qw: Postable[Q, W, ID]) : Postable[P :: Q, W, ID] =
    {
     def post(content: P :: Q, web: W, pred: Set[ID]): Future[ID] = 
      content match {
        case p :: q => 
          qw.post(q, web, pred).flatMap(id => pw.post(p, web, Set(id)))
      }
    Impl(post)
    }

  /**
    * Post a `Right[P]`, to be used to split the stream with a change or not.
    *
    * @param pw postability of `P`
    * @param uw postability of `Unit`
    * @return Postability of `Right[P]`
    */
  implicit def rightPost[X : TypeTag, P : TypeTag, W, ID](implicit pw: Postable[P, W, ID], uw: Postable[Unit, W, ID]) : Postable[Right[X, P], W, ID] = 
    {
      def post(content: Right[X, P], web: W, pred: Set[ID]): Future[ID] = 
        {
          uw.post((), web, pred).foreach(_ => ())
          pw.post(content.value, web, pred)
        }
        
        Impl(post)
    }


}

/**
  * Wrapper for post content that should be posted, with the previous elements of the same type also posted, in general with transformations (e.g. rescale)
  *
  * @param content the content to be posted
  * @param transformation transformations of other posts, typically rescaling
  * @param pw postability of P
  * @param pq queryability of P
  */
case class SplitPost[P : TypeTag,  Q: TypeTag, W: TypeTag, ID: TypeTag](content: P, transformation: Q => P)(implicit val pw: Postable[P, W, ID], val qq : LocalQueryable[Q, W, ID])

object SplitPost{
  def simple[P : TypeTag, W : TypeTag, ID: TypeTag](content: P)(implicit pw: Postable[P, W, ID], qq : LocalQueryable[P, W, ID]) : SplitPost[P,P,W,ID] = 
    SplitPost[P, P, W, ID](content, identity[P](_))

  def some[P : TypeTag, W : TypeTag, ID: TypeTag](content: P)(implicit pw: Postable[P, W, ID], qq : LocalQueryable[Some[P], W, ID]) : SplitPost[P,Some[P],W,ID] =
    SplitPost[P, Some[P], W, ID](content, _.value)

  implicit def splitPostable[P : TypeTag,  Q: TypeTag, W: TypeTag, ID: TypeTag]: Postable[SplitPost[P, Q, W, ID], W, ID] = {
    def post(content: SplitPost[P, Q, W, ID], web: W, pred: Set[ID]): Future[ID] = {
      content.pw.post(content.content, web, pred).map{
        postID =>
          val othersFutVec = content.qq.getAt(web, postID, (_) => true)
          othersFutVec.foreach{
            v => v.foreach{
              x => content.pw.post(content.transformation(x), web, pred)
            }
          }
          postID
      }
    }
    Postable.Impl(post)
  }
}

trait GlobalID[ID]{
  def postGlobal[P](content: P) : Future[ID]
}

/**
  * allows posting globally and keeps count without stroing anything
  *
  * @param log logging on post
  */
class CounterGlobalID(log : Any => Unit = (_) => ()) extends GlobalID[(Int, Int)]{
  var counter: Int = 0

  /**
    * post arbitrary content
    *
    * @param content content of some type
    * @return ID, consisting of an index and a hashCode
    */
  def postGlobal[P](content: P) : Future[(Int, Int)] = {
    val index = counter
    counter +=1
    log(content)
    Future((counter, content.hashCode()))
  }
}