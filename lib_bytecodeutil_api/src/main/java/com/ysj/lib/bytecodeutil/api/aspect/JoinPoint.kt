package com.ysj.lib.bytecodeutil.api.aspect

import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * 方法连接点
 *
 * @author Ysj
 * Create time: 2021/3/8
 */
class JoinPoint(
    /** 切入点的 this 获取的对象，若切入点在静态方法内，则为 null */
    val target: Any?,
    /** 切入点方法的参数 */
    val args: Array<Any?>,
) : Serializable {
    companion object {
        private val STORE = ConcurrentHashMap<String, JoinPoint>()

        @JvmStatic
        fun put(key: String, value: JoinPoint) {
            STORE["${Thread.currentThread().id}-$key"] = value
        }

        @JvmStatic
        fun remove(key: String) {
            STORE.remove("${Thread.currentThread().id}-$key")
        }

        @JvmStatic
        fun get(key: String) = STORE["${Thread.currentThread().id}-$key"]
    }
}