package app.otium.di

import app.otium.domain.port.BackupScheduler
import app.otium.domain.port.LookupScheduler
import app.otium.work.WorkManagerBackupScheduler
import app.otium.work.WorkManagerLookupScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Background work. Its own module so tests can swap scheduling out without WorkManager. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    abstract fun bindLookupScheduler(impl: WorkManagerLookupScheduler): LookupScheduler

    @Binds
    abstract fun bindBackupScheduler(impl: WorkManagerBackupScheduler): BackupScheduler
}
