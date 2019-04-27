package provingground.learning
import provingground._
import HoTT._
import monix.eval.Task

import scala.concurrent._
import duration._

import TermRandomVars._

import GeneratorVariables._

import EntropyAtomWeight._

import scalahott.NatRing

/**
 * Collect local/generative/tactical proving;
 * this includes configuration and learning but excludes strategy and attention.
 * This can be called in a loop generating goals based on unproved theorems,
 * using representation/deep learning with attention focussed or interactively.
 */
case class LocalProver(
    initState: TermState =
      TermState(FiniteDistribution.empty, FiniteDistribution.empty),
    tg: TermGenParams = TermGenParams(),
    cutoff: Double = math.pow(10, -4),
    limit: FiniteDuration = 3.minutes,
    maxRatio: Double = 1.01,
    scale: Double = 1.0,
    steps: Int = 10000,
    maxDepth: Int = 10,
    hW: Double = 1,
    klW: Double = 1
) extends LocalProverStep {
  // Convenience for generation
  def sharpen(scale: Double = 2.0) = this.copy(cutoff = cutoff/scale)

  def addTerms(terms: (Term, Double)*): LocalProver = {
    val total = terms.map(_._2).sum
    val td = initState.terms * (1 - total) ++ FiniteDistribution(terms.map {
      case (x, p) => Weighted(x, p)
    })
    val ts = initState.copy(terms = td)
    this.copy(initState = ts)
  }

  lazy val typesByRestrict: LocalProver = {
    val typd = initState.terms.collect { case tp: Typ[Term] => tp }.safeNormalized
    val ts   = initState.copy(typs = typd)
    this.copy(initState = ts)
  }

  lazy val typesByMap: LocalProver = {
    val typd = initState.terms.map(_.typ)
    val ts   = initState.copy(typs = typd)
    this.copy(initState = ts)
  }

  def addVars(terms: (Term, Double)*): LocalProver = {
    val total = terms.map(_._2).sum
    val td = initState.terms * (1 - total) ++ FiniteDistribution(terms.map {
      case (x, p) => Weighted(x, p)
    })
    val allVars = initState.vars ++ terms.map(_._1).toVector
    val ctx = terms.foldLeft(initState.context) {
      case (cx: Context, (y, _)) => cx.addVariable(y)
    }
    val ts =
      initState.copy(terms = td.safeNormalized, vars = allVars, context = ctx)
    this.copy(initState = ts)
  }

  def addTypes(typs: (Typ[Term], Double)*): LocalProver = {
    val total = typs.map(_._2).sum
    val typd = initState.typs * (1 - total) ++ FiniteDistribution(typs.map {
      case (x, p) => Weighted(x, p)
    })
    val ts = initState.copy(typs = typd.safeNormalized)
    this.copy(initState = ts)
  }

  def addAxioms(typs: (Typ[Term], Double)*): LocalProver = {
    val total = typs.map(_._2).sum
    val td = initState.terms * (1 - total) ++ FiniteDistribution(typs.map {
      case (x, p) => Weighted("axiom" :: x, p)
    })
    val ts = initState.copy(terms = td.safeNormalized)
    this.copy(initState = ts)
  }

  def addGoals(typs: (Typ[Term], Double)*): LocalProver = {
    val total = typs.map(_._2).sum
    val typd = initState.goals * (1 - total) ++ FiniteDistribution(typs.map {
      case (x, p) => Weighted(x, p)
    })
    val ts = initState.copy(goals = typd.safeNormalized)
    this.copy(initState = ts)
  }

  def addInd(
      typ: Typ[Term],
      intros: Term*
  )(params: Vector[Term] = Vector(), weight: Double = 1): LocalProver = {
    import induction._
    val str0 = ExstInducStrucs.get(typ, intros.toVector)
    val str = params.foldRight(str0) {
      case (x, s) => ExstInducStrucs.LambdaInduc(x, s)
    }
    val dfn     = ExstInducDefn(typ, intros.toVector, str)
    val indsNew = initState.inds * (1 - weight) ++ FiniteDistribution.unif(dfn)
    val ts      = initState.copy(inds = indsNew)
    this.copy(initState = ts)
  }

  def addIndexedInd(
      typ: Term,
      intros: Term*
  )(params: Vector[Term] = Vector(), weight: Double = 1): LocalProver = {
    import induction._
    val str0 = ExstInducStrucs.getIndexed(typ, intros.toVector)
    val str = params.foldRight(str0) {
      case (x, s) => ExstInducStrucs.LambdaInduc(x, s)
    }
    val dfn     = ExstInducDefn(typ, intros.toVector, str)
    val indsNew = initState.inds * (1 - weight) ++ FiniteDistribution.unif(dfn)
    val ts      = initState.copy(inds = indsNew)
    this.copy(initState = ts)
  }

  lazy val noIsles: LocalProver = this.copy(tg = tg.copy(lmW = 0, piW = 0))

  def isleWeight(w: Double): LocalProver = this.copy(tg = tg.copy(lmW = w, piW = w))

  def backwardWeight(w: Double): LocalProver =
    this.copy(tg = tg.copy(typAsCodW = w, targetInducW = w))

  def negateTypes(w: Double): LocalProver = this.copy(tg = tg.copy(negTargetW = w))

  def natInduction(w: Double = 1.0): LocalProver = {
    val mixin = initState.inds + (NatRing.exstInducDefn, w)
    this.copy(initState.copy(inds = mixin.safeNormalized))
  }


  // Proving etc
  val nextState: Task[TermState] =
    tg.nextStateTask(initState, cutoff, limit).memoize

  val mfd: MonixFiniteDistributionEq[TermState, Term] =
    MonixFiniteDistributionEq(tg.nodeCoeffSeq)

  val pairT: Task[(FiniteDistribution[Term], Set[EquationNode])] =
    mfd.varDist(initState)(Terms, cutoff)

  val equationTerms: Task[Set[EquationNode]] = pairT.map(_._2).memoize

  // Generating provers using results
  val withLemmas: Task[LocalProver] =
    for {
      lws <- lemmas
      lfd = FiniteDistribution(lws.map {
        case (tp, p) => Weighted("lemma" :: tp, p)
      })
      lInit = initState.copy(terms = (initState.terms ++ lfd).safeNormalized)
    } yield this.copy(initState = lInit)

  val optimalInit: Task[LocalProver] = expressionEval.map { ev =>
    val p                            = ev.optimum(hW, klW)
    val td: FiniteDistribution[Term] = ExpressionEval.dist(Terms, p)
    val ts                           = initState.copy(terms = td)
    this.copy(initState = ts)
  }

}

trait LocalProverStep {
  val initState: TermState
  val nextState: Task[TermState]
  val tg: TermGenParams
  val cutoff: Double
  val scale: Double
  val steps: Int
  val maxDepth: Int
  val limit: FiniteDuration

  val evolvedState: Task[EvolvedState] = nextState
    .map(
      result => EvolvedState(initState, result, tg, cutoff)
    )
    .memoize

  val theoremsByStatement: Task[FiniteDistribution[Typ[Term]]] =
    nextState.map(_.thmsBySt)

  val theoremsByProof: Task[FiniteDistribution[Typ[Term]]] =
    nextState.map(_.thmsByPf)

  val unknownStatements: Task[FiniteDistribution[Typ[Term]]] =
    nextState.map(_.unknownStatements)

  val equationTerms: Task[Set[EquationNode]]

  val equations: Task[Set[Equation]] = equationTerms.map { eqs =>
    groupEquations(eqs)
  }.memoize

  val expressionEval: Task[ExpressionEval] =
    (for {
      fs  <- nextState
      eqs <- equations
    } yield ExpressionEval(initState, fs, eqs, tg)).memoize

  val lemmas: Task[Vector[(Typ[Term], Double)]] =
    (for {
      ev <- evolvedState
    } yield lemmaWeights(ev, steps, scale)).memoize

  val seek: Task[FiniteDistribution[Term]] =
    for {
      base <- nextState
      goals <- findGoal(
        initState,
        base,
        tg,
        steps,
        maxDepth,
        cutoff,
        limit,
        scale
      )
    } yield goals

  val functionsForGoals: Task[FiniteDistribution[Term]] =
    (nextState
      .flatMap { fs: TermState =>
        Task.gather(fs.remainingGoals.pmf.map {
          case Weighted(goal, p) =>
            val codomainTarget: Task[FiniteDistribution[Term]] = tg.monixFD
              .nodeDist(initState)(tg.Gen.codomainNode(goal), cutoff)
              .map(_ * p)
            val inductionTarget: Task[FiniteDistribution[Term]] = tg.monixFD
              .nodeDist(initState)(tg.Gen.targetInducBackNode(goal), cutoff)
              .map(_ * p)
            for {
              dcd <- codomainTarget
              did <- inductionTarget
            } yield (dcd ++ did).safeNormalized
        })
      })
      .map(vfd => vfd.foldLeft(FiniteDistribution.empty[Term])(_ ++ _))

  val subgoals: Task[FiniteDistribution[Typ[Term]]] =
    for {
      funcs <- functionsForGoals
      fs    <- nextState
    } yield funcs.flatMap(fs.subGoalsFromFunc _)

}
