sealed class SealedClass
class SealedInheritorA: SealedClass()
class SealedInheritorB: SealedClass()

fun test(x: SealedClass): Any = when (x) {
        is SealedInheritorA -> TODO()
        is SealedInheritorB -> TODO()
    }
}
