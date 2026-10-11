package org.view2earn.vinta.rn

import android.content.Context
import android.widget.FrameLayout
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManager
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.views.view.ReactViewGroup
import org.view2earn.vinta.VintaBannerView

/** Registered by autolinking. */
class VintaPackage : ReactPackage {
    // Legacy package API: still supported on RN 0.86 (New Architecture interop).
    @Suppress("OVERRIDE_DEPRECATION")
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> = listOf(VintaModule(reactContext))
    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        listOf(VintaBannerManager(), VintaNativeAdViewManager())
}

/**
 * Banner host. RN lays out native views itself and ignores requestLayout from
 * views that change after an async load, so the loaded image would stay 0x0;
 * re-measure manually (the standard RN workaround).
 */
class RNVintaBanner(context: Context) : FrameLayout(context) {
    val banner = VintaBannerView(context).also { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
    private val relayout = Runnable {
        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        layout(left, top, right, bottom)
    }

    override fun requestLayout() {
        super.requestLayout()
        post(relayout)
    }
}

class VintaBannerManager : SimpleViewManager<RNVintaBanner>() {
    override fun getName() = "VintaBannerView"
    override fun createViewInstance(ctx: ThemedReactContext) = RNVintaBanner(ctx)

    @ReactProp(name = "unitId")
    fun setUnitId(view: RNVintaBanner, unitId: String?) {
        if (!unitId.isNullOrBlank()) view.banner.load(unitId)
    }

    override fun onDropViewInstance(view: RNVintaBanner) {
        view.banner.destroy()
        super.onDropViewInstance(view)
    }
}

/**
 * Container for a native ad laid out in JS. Registers itself with the SDK so
 * the impression is counted when it's 50% visible for 1s. The "Ad" label is
 * drawn by the JS component (RN owns this view's children).
 */
class VintaNativeAdView(context: Context) : ReactViewGroup(context) {
    var token: String? = null
        set(value) {
            field = value
            register()
        }

    private fun register() {
        val ad = token?.let { VintaModule.natives[it] } ?: return
        if (isAttachedToWindow) ad.registerView(this, null, addLabel = false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        register()
    }

    override fun onDetachedFromWindow() {
        token?.let { VintaModule.natives[it] }?.unregister()
        super.onDetachedFromWindow()
    }
}

class VintaNativeAdViewManager : ViewGroupManager<VintaNativeAdView>() {
    override fun getName() = "VintaNativeAdView"
    override fun createViewInstance(ctx: ThemedReactContext) = VintaNativeAdView(ctx)

    @ReactProp(name = "adToken")
    fun setAdToken(view: VintaNativeAdView, token: String?) {
        view.token = token
    }
}

