package android.kimyona.jammer.util

import java.lang.ref.WeakReference

abstract class WeakRefRunnable<T>(target: T) : Runnable {
    private val weakRef = WeakReference(target)

    final override fun run() {
        val target = weakRef.get()
        if (target != null) {
            runWithRef(target)
        }
    }
    abstract fun runWithRef(target: T)
}
