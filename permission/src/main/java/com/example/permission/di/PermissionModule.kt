package com.example.permission.di

import android.Manifest
import android.app.Activity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.example.permission.PermissionController
import com.example.permission.dialog.CameraPermissionReqDialog
import com.example.permission.dialog.CommonPermissionReqDialog
import com.example.permission.dialog.IPermissionReqDialog
import com.example.permission.dialog.LocationPermissionReqDialog
import com.example.permission.dialog.StoragePermissionReqDialog
import com.example.permission.kv.PermissionReqRepo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/** 自动向宿主提供权限记录仓库，保持每次请求创建实例的行为。 */
@Module
@InstallIn(SingletonComponent::class)
object PermissionModule {
    @Provides
    fun providePermissionReqRepo(): PermissionReqRepo = PermissionReqRepo()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule2 {

    @Binds
    @IntoMap
    @StringKey(Manifest.permission.CAMERA)
    abstract fun bindCameraReqDialog(impl: CameraPermissionReqDialog): IPermissionReqDialog

    @Binds
    @IntoMap
    @StringKey(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    abstract fun bindWriteStorageReqDialog(impl: StoragePermissionReqDialog): IPermissionReqDialog

    @Binds
    @IntoMap
    @StringKey("${Manifest.permission.ACCESS_FINE_LOCATION},${Manifest.permission.ACCESS_COARSE_LOCATION}")
    abstract fun bindLocationReqDialog(impl: LocationPermissionReqDialog): IPermissionReqDialog

}

@Module
@InstallIn(SingletonComponent::class)
object PermissionFragmentModule {
    @Provides
    fun providerPermissionRequestFragment(
        fragment: Fragment,
        reqMap: Map<String, @JvmSuppressWildcards IPermissionReqDialog>
    ) = PermissionController(fragment, reqMap)

    @Provides
    fun providerPermissionRequestActivity(
        activity: FragmentActivity,
        reqMap: Map<String, @JvmSuppressWildcards IPermissionReqDialog>
    ) = PermissionController(activity, reqMap)

}

