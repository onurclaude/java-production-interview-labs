package com.javalabs.concurrency.common;

/**
 * Lab 1-4 ve CPU-bound lab'ının karşılaştırdığı thread/executor modelleri.
 * Tek bir enum altında toplanmıştır ki response'lar arasında "implementation" alanı tutarlı olsun.
 */
public enum ThreadModel {
    PLATFORM_THREAD,
    VIRTUAL_THREAD,
    FIXED_THREAD_POOL,
    BOUNDED_THREAD_POOL,
    CPU_BOUND_PLATFORM_POOL,
    CPU_BOUND_VIRTUAL
}
