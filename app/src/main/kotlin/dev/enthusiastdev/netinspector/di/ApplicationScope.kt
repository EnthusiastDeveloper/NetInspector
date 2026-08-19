package dev.enthusiastdev.netinspector.di

import javax.inject.Qualifier

/** A crashing probe/collector must not tear down unrelated ones (design §2.4). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
