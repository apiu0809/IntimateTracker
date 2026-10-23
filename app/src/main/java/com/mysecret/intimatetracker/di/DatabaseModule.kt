package com.mysecret.intimatetracker.di

import android.content.Context
import androidx.room.Room
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.LocationDao
import com.mysecret.intimatetracker.data.db.Migrations
import com.mysecret.intimatetracker.data.db.PartnerDao
import com.mysecret.intimatetracker.data.db.PhotoDao
import com.mysecret.intimatetracker.data.db.PositionDao
import com.mysecret.intimatetracker.data.db.RecordDao
import com.mysecret.intimatetracker.data.db.SettingsDao
import com.mysecret.intimatetracker.data.db.StatsDao
import com.mysecret.intimatetracker.data.db.TemplateDao
import com.mysecret.intimatetracker.data.db.SexTypeDao
import com.mysecret.intimatetracker.data.db.ToyDao
import dagger.Module
import com.mysecret.intimatetracker.data.sync.OkHttpWebDavFactory
import com.mysecret.intimatetracker.data.sync.WebDavClientFactory
import dagger.Binds
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun providePartnerDao(db: AppDatabase): PartnerDao = db.partnerDao()

    @Provides
    fun provideSexTypeDao(db: AppDatabase): SexTypeDao = db.sexTypeDao()

    @Provides
    fun providePositionDao(db: AppDatabase): PositionDao = db.positionDao()

    @Provides
    fun provideToyDao(db: AppDatabase): ToyDao = db.toyDao()

    @Provides
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()

    @Provides
    fun provideRecordDao(db: AppDatabase): RecordDao = db.recordDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun providePhotoDao(db: AppDatabase): PhotoDao = db.photoDao()

    @Provides
    fun provideTemplateDao(db: AppDatabase): TemplateDao = db.templateDao()

    @Provides
    fun provideStatsDao(db: AppDatabase): StatsDao = db.statsDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindWebDavFactory(impl: OkHttpWebDavFactory): WebDavClientFactory
}