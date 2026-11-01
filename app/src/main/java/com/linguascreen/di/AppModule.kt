package com.linguascreen.di

import android.content.Context
import androidx.room.Room
import com.linguascreen.data.db.AppDatabase
import com.linguascreen.data.db.HistoryDao
import com.linguascreen.data.ocr.MlKitOcrEngine
import com.linguascreen.data.translate.MlKitTranslationProvider
import com.linguascreen.domain.OcrEngine
import com.linguascreen.domain.TranslationProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun ocr(impl: MlKitOcrEngine): OcrEngine

    /** Swap this binding to plug in another translation provider. */
    @Binds abstract fun translation(impl: MlKitTranslationProvider): TranslationProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "linguascreen.db").build()

    @Provides
    fun historyDao(db: AppDatabase): HistoryDao = db.historyDao()
}
