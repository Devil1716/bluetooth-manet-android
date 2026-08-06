package com.devil1716.bluetoothmanet.di

import android.content.Context
import com.devil1716.bluetoothmanet.crypto.IdentityStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MeshModule {
    @Provides @Singleton
    fun identityStore(@ApplicationContext context: Context): IdentityStore = IdentityStore(context)
}
