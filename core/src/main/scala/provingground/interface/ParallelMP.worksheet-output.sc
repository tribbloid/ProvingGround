
// Modus Ponens proved using parallel generation
import provingground._ , learning._, interface._, translation._, HoTT._, induction._
val A = Type.sym
// A: Typ[Term] = SymbTyp(name = Name(name = "A"), level = 0)
val B = Type.sym
// B: Typ[Term] = SymbTyp(name = Name(name = "B"), level = 0)
val ns = ParMapState.parNodeSeq(TermGenParams())
// ns: TermNodeCoeffSeq[ParMapState] = provingground.learning.TermNodeCoeffSeq$$anon$1@5c7b841e
import scala.collection.parallel._, immutable.ParVector
val state0 = ParMapState(ParMap(), ParMap(Type -> 1.0))
// state0: ParMapState = ParMapState(
//   termDist = ParHashMap(),
//   typDist = ParHashMap(𝒰 _0 -> 1.0),
//   vars = Vector(),
//   inds = ParHashMap(),
//   goalDist = ParHashMap(),
//   context = {}
// )
val pde = new ParDistEq(ns.nodeCoeffSeq)
// pde: ParDistEq = provingground.learning.ParDistEq@7b7d2f06
val MP = A ~>: (B ~>: (A ->: (A ->: B) ->: B))
// MP: GenFuncTyp[Typ[Term], FuncLike[Typ[Term], Func[Term, Func[Func[Term, Term], Term]]]] = PiDefn(
//   variable = SymbTyp(name = `A, level = 0),
//   value = PiDefn(
//     variable = SymbTyp(name = `B, level = 0),
//     value = FuncTyp(
//       dom = SymbTyp(name = `A, level = 0),
//       codom = FuncTyp(
//         dom = FuncTyp(dom = SymbTyp(name = `A, level = 0), codom = SymbTyp(name = `B, level = 0)),
//         codom = SymbTyp(name = `B, level = 0)
//       )
//     )
//   )
// )
import GeneratorVariables._, Expression._, TermRandomVars._
lazy val (mpts1, mpeq1) = pde.varDist(state0, None, false)(termsWithTyp(MP), math.pow(10, -3))
val pfs1 = mpts1.keys.to(Set)
// pfs1: Set[Term] = Set(
//   LambdaTerm(
//     variable = SymbTyp(name = `@a, level = 0),
//     value = LambdaTerm(
//       variable = SymbTyp(name = `@b, level = 0),
//       value = LambdaFixed(
//         variable = SymbObj(name = ``@a, typ = SymbTyp(name = `@a, level = 0)),
//         value = LambdaFixed(
//           variable = SymbolicFunc(
//             name = ```@a,
//             dom = SymbTyp(name = `@a, level = 0),
//             codom = SymbTyp(name = `@b, level = 0)
//           ),
//           value = SymbObj(
//             name = ApplnSym(
//               func = SymbolicFunc(
//                 name = ```@a,
//                 dom = SymbTyp(name = `@a, level = 0),
//                 codom = SymbTyp(name = `@b, level = 0)
//               ),
//               arg = SymbObj(name = ``@a, typ = SymbTyp(name = `@a, level = 0))
//             ),
//             typ = SymbTyp(name = `@b, level = 0)
//           )
//         )
//       )
//     )
//   )
// )
println(pfs1.head)
// (`@a :  𝒰 _0) ↦ ((`@b :  𝒰 _0) ↦ ((``@a :  `@a) ↦ ((```@a :  (`@a) → (`@b)) ↦ ((```@a) (``@a)))))
val state1 = ParMapState(ParMap(), ParMap(Type -> 1.0), goalDist = ParMap(MP -> 1.0))
// state1: ParMapState = ParMapState(
//   termDist = ParHashMap(),
//   typDist = ParHashMap(𝒰 _0 -> 1.0),
//   vars = Vector(),
//   inds = ParHashMap(),
//   goalDist = ParHashMap((`A : 𝒰 _0 ) ~> ((`B : 𝒰 _0 ) ~> ((`A) → (((`A) → (`B)) → (`B)))) -> 1.0),
//   context = {}
// )
val pde1  = new ParDistEq(ns.nodeCoeffSeq)
// pde1: ParDistEq = provingground.learning.ParDistEq@48441daa
val (nextState, eqs) = pde1.nextStateEqs(state1, math.pow(10, -4))
// nextState: ParMapState = ParMapState(
//   termDist = ParMap((((`@a_1) , (`@a_2)) :  ∑((`@a :  𝒰 _0) ↦ (`@a))) ↦ (((`@a_1) , (`@a_2))) -> 0.003010533875189331, (((`@a_1) , (@a_2)) :  ((𝒰 _0) , (𝒰 _0))) ↦ (((`@a_1) , (@a_2))) -> 0.005943874574091756, (`@a :  (`@a : 𝒰 _0 ) ~> (`@a)) ↦ ((`@a) ((`@a) (𝒰 _0))) -> 7.61891462570315E-4, (((`@a_1) , (@a_2)) :  (((𝒰 _0) → (𝒰 _0)) , (𝒰 _0))) ↦ (((`@a_1) , (@a_2))) -> 0.0014172088217453778, (((`@a_1) , (@a_2)) :  (((`@a : 𝒰 _0 ) ~> (`@a)) , (𝒰 _0))) ↦ (((`@a_1) , (@a_2))) -> 6.073752093194475E-4, (`@a :  𝒰 _0) ↦ ((``@a :  `@a) ↦ ((@b :  𝒰 _0) ↦ (``@a))) -> 2.1762131068281157E-4, (((`@a_1) , (((@a_2_1) , (@a_2_2)))) :  ((𝒰 _0) , (((𝒰 _0) , (𝒰 _0))))) ↦ (((`@a_1) , (((@a_2_1) , (@a_2_2))))) -> 8.362594097748145E-4, (@a :  𝒰 _0) ↦ ((((`@a_1) , (`@a_2)) :  ∑((`@b :  𝒰 _0) ↦ (`@b))) ↦ (((`@a_1) , (`@a_2)))) -> 2.746019357488408E-4, (`@a :  𝒰 _0) ↦ ((((@a_1) , (@a_2)) :  ∑((`@b :  𝒰 _0) ↦ (`@b))) ↦ (`@a)) -> 1.9222135502418853E-4, (`@a :  𝒰 _0) ↦ ((`@b :  𝒰 _0) ↦ ((``@a :  `@a) ↦ ((```@a :  (`@a) → (`@b)) ↦ ((```@a) (``@a))))) -> 0.10819506902561746, (`@a :  𝒰 _0) ↦ (`@a) -> 0.780040180462644, (`@a :  𝒰 _0) ↦ ((@a :  (𝒰 _0) → (𝒰 _0)) ↦ (`@a)) -> 3.472414704726956E-4, (@a :  (𝒰 _0) → (𝒰 _0)) ↦ ((`@a :  𝒰 _0) ↦ (`@a)) -> 0.001206615438459775, (`@a :  𝒰 _0) ↦ ((`@a :  (`@a) → (𝒰 _0)) ↦ (`@a)) -> 2.1259681865675248E-4, (`@a :  (𝒰 _0) → ((𝒰 _0) → (𝒰 _0))) ↦ (`@a) -> 0.0010888698819234086, (`@a :  (`@a : 𝒰 _0 ) ~> ((`@a) → (𝒰 _0))) ↦ (`@a) -> 6.666550297490256E-4, (`@a :  𝒰 _0) ↦ ((@a :  (`@b : 𝒰 _0 ) ~> (`@b)) ↦ (`@a)) -> 2.1259681865675256E-4, (@a :  (`@a : 𝒰 _0 ) ~> (`@a)) ↦ ((`@a :  𝒰 _0) ↦ (`@a)) -> 4.481714485707736E-4, (@a :  𝒰 _0) ↦ ((`@a :  (`@b : 𝒰 _0 ) ~> (`@b)) ↦ (`@a)) -> 3.0370974093821794E-4, (`@a :  (`@a : 𝒰 _0 ) ~> (`@a)) ↦ ((@a :  𝒰 _0) ↦ (`@a)) -> 3.1372001399954146E-4, (`@a :  (`@a : 𝒰 _0 ) ~> (`@a)) ↦ (`@a) -> 0.004190403044136734, (((`@a_1) , (@a_2)) :  ((𝒰 _0) , ((𝒰 _0) → (𝒰 _0)))) ↦ (((`@a_1) , (@a_2))) -> 5.736739551055226E-4, (𝒰 _0) → (𝒰 _0) -> 0.03201202714897042, (`@a :  𝒰 _0) ↦ ((`@a :  `@a) ↦ ((`@b :  𝒰 _0) ↦ (`@b))) -> 3.1088758668973063E-4, (`@a :  𝒰 _0) ↦ ((`@a :  `@a) ↦ ((@b :  𝒰 _0) ↦ (`@a))) -> 1.5233491747796807E-4, (@a :  𝒰 _0) ↦ ((((`@a_1) , (@a_2)) :  ((𝒰 _0) , (𝒰 _0))) ↦ (((`@a_1) , (@a_2)))) -> 4.485164950564398E-4, (((`@a_1) , (@a_2)) :  ((𝒰 _0) , (𝒰 _0))) ↦ ((@a :  𝒰 _0) ↦ (((`@a_1) , (@a_2)))) -> 4.44995957418634E-4, (`@a :  (𝒰 _0) → (((𝒰 _0) , (𝒰 _0)))) ↦ (`@a) -> 0.001587273880354823, (`@a :  𝒰 _0) ↦ ((@b :  𝒰 _0) ↦ ((@c :  𝒰 _0) ↦ (`@a))) -> 3.5544814078192547E-4, (@a :  𝒰 _0) ↦ ((@b :  𝒰 _0) ↦ ((`@c :  𝒰 _0) ↦ (`@c))) -> 7.25404368942705E-4, (@a :  𝒰 _0) ↦ ((`@b :  𝒰 _0) ↦ ((@c :  𝒰 _0) ↦ (`@b))) -> 5.077830582598935E-4, (`@a :  (∑((`@a :  𝒰 _0) ↦ (`@a))) → (𝒰 _0)) ↦ (`@a) -> 5.952277051330586E-4, (`@a :  ((`@a : 𝒰 _0 ) ~> (`@a)) → (𝒰 _0)) ↦ (`@a) -> 0.0011904554102661173, (`@a :  ((𝒰 _0) → (𝒰 _0)) → (𝒰 _0)) ↦ (`@a) -> 0.00277772929062094, (`@a :  (((𝒰 _0) , (𝒰 _0))) → (𝒰 _0)) ↦ (`@a) -> 0.00138886464531047, (`@a :  (𝒰 _0) → (𝒰 _0)) ↦ (`@a) -> 0.016138879021683015, (`@a :  𝒰 _0) ↦ ((``@a :  (`@a) → (𝒰 _0)) ↦ (``@a)) -> 3.037097409382179E-4, (@a :  𝒰 _0) ↦ ((`@a :  (𝒰 _0) → (𝒰 _0)) ↦ (`@a)) -> 4.960592435324225E-4, (`@a :  (𝒰 _0) → (𝒰 _0)) ↦ ((@a :  𝒰 _0) ↦ (`@a)) -> 8.446308069218426E-4, (((((`@a_1_1) , (@a_1_2))) , (@a_2)) :  ((((𝒰 _0) , (𝒰 _0))) , (𝒰 _0))) ↦ (((((`@a_1_1) , (@a_1_2))) , (@a_2))) -> 7.086044108726889E-4, (`@a :  𝒰 _0) ↦ ((``@a :  `@a) ↦ (``@a)) -> 0.002202729989222239, (`@a :  𝒰 _0) ↦ ((((@a_1) , (@a_2)) :  ((𝒰 _0) , (𝒰 _0))) ↦ (`@a)) -> 3.1396154653950785E-4, (((@a_1) , (@a_2)) :  ((𝒰 _0) , (𝒰 _0))) ↦ ((`@a :  𝒰 _0) ↦ (`@a)) -> 6.357085105980487E-4, (`@a :  (`@a : 𝒰 _0 ) ~> ((𝒰 _0) → (`@a))) ↦ (`@a) -> 4.6665852082431803E-4, (`@a :  (𝒰 _0) → ((`@b : 𝒰 _0 ) ~> (`@b))) ↦ (`@a) -> 6.666550297490258E-4, (`@a :  𝒰 _0) ↦ ((`@a :  `@a) ↦ (`@a)) -> 0.0015419109924555672, (`@a :  𝒰 _0) ↦ ((@b :  𝒰 _0) ↦ (`@a)) -> 0.0035977923157296567, (@a :  𝒰 _0) ↦ ((`@b :  𝒰 _0) ↦ (`@b)) -> 0.01852466016257293),
//   typDist = ParMap((((((𝒰 _0) → (𝒰 _0)) , (𝒰 _0))) , (𝒰 _0)) -> 1.7380044774093677E-4, ((𝒰 _0) , ((`@b : 𝒰 _0 ) ~> (`@b))) -> 6.510806805179113E-4, ((𝒰 _0) , ((((𝒰 _0) → (𝒰 _0)) , (𝒰 _0)))) -> 1.1286659814454668E-4, ((𝒰 _0) , (𝒰 _0)) -> 0.02933606776243578, (𝒰 _0) → (((((𝒰 _0) , (𝒰 _0))) , (𝒰 _0))) -> 1.9783856666943424E-4, (`@a : 𝒰 _0 ) ~> (((`@a) , (`@a))) -> 3.4448135731094147E-4, ((𝒰 _0) , (((𝒰 _0) , (𝒰 _0)))) -> 6.7719958886728E-4, ∑((`@a :  𝒰 _0) ↦ (((𝒰 _0) , (`@a)))) -> 2.902283952288344E-4, (𝒰 _0) → (∑((`@b :  𝒰 _0) ↦ (`@b))) -> 8.037898337255302E-4, (((𝒰 _0) → (𝒰 _0)) , ((`@a : 𝒰 _0 ) ~> (`@a))) -> 1.0276840634597434E-4, 𝒰 _0 -> 0.85, (((𝒰 _0) → (𝒰 _0)) , (((𝒰 _0) , (𝒰 _0)))) -> 1.7128067724329055E-4, (((`@a : 𝒰 _0 ) ~> (`@a)) , (𝒰 _0)) -> 9.860508071575364E-4, (`@a : 𝒰 _0 ) ~> (((𝒰 _0) , (`@a))) -> 5.626528836078711E-4, ((((𝒰 _0) , (𝒰 _0))) , (𝒰 _0)) -> 0.001205407311081235, (((𝒰 _0) → ((`@b : 𝒰 _0 ) ~> (`@b))) , (𝒰 _0)) -> 1.2846050793246798E-4, (((𝒰 _0) → (𝒰 _0)) , ((𝒰 _0) → (𝒰 _0))) -> 2.3979294814060675E-4, ((((𝒰 _0) , (𝒰 _0))) , ((𝒰 _0) → (𝒰 _0))) -> 1.4063085295947737E-4, (((𝒰 _0) → (∑((`@b :  𝒰 _0) ↦ (`@b)))) , (𝒰 _0)) -> 1.161487413494285E-4, (((𝒰 _0) → (((𝒰 _0) , (𝒰 _0)))) , (𝒰 _0)) -> 1.8970961087073327E-4, ∑((`@a :  𝒰 _0) ↦ ((`@a) → (𝒰 _0))) -> 6.510806805179113E-4, ∑((`@a :  𝒰 _0) ↦ (((`@a) , (𝒰 _0)))) -> 4.1461199318404906E-4, (`@a : 𝒰 _0 ) ~> ((`@a) → ((`@b : 𝒰 _0 ) ~> (`@b))) -> 9.015865444915704E-5, (`@a : 𝒰 _0 ) ~> ((`@a) → ((𝒰 _0) → (`@a))) ->...
// eqs: ParSet[EquationNode] = <function1>
val mpProof = nextState.termDist.filter(_._1.typ == MP)
// mpProof: ParMap[Term, Double] = ParHashMap((`@a :  𝒰 _0) ↦ ((`@b :  𝒰 _0) ↦ ((``@a :  `@a) ↦ ((```@a :  (`@a) → (`@b)) ↦ ((```@a) (``@a))))) -> 0.10819506902561746)