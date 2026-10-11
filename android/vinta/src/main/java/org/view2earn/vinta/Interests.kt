package org.view2earn.vinta

import android.app.Activity
import android.app.AlertDialog
import android.content.Context

/**
 * User-chosen interests (blueprint 0 + 24.9). The user picks them, they are
 * saved only on this device, and they travel with each ad request so ads
 * match what the user asked for. VINTA's server never stores them.
 * Keep the ids in sync with INTERESTS in convex/vinta/money.ts.
 */
object VintaInterests {
    /** id → label shown in the picker. */
    @JvmStatic
    val OPTIONS: Map<String, String> = linkedMapOf(
        "islamic_education" to "Islamic learning",
        "islamic_finance_takaful" to "Islamic finance & Takaful",
        "halal_food" to "Halal food",
        "modest_fashion" to "Modest fashion",
        "hajj_umrah" to "Hajj & Umrah",
        "apps_utilities" to "Apps & tools",
        "education" to "Education",
        "ecommerce" to "Shopping",
        "telecom" to "Phones & data",
        "music" to "Music",
        "entertainment_media" to "Films & shows",
        "fashion_beauty" to "Fashion & beauty",
        "crypto" to "Crypto & blockchain",
    )

    private const val KEY = "interests"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("vinta", Context.MODE_PRIVATE)

    /** The user's saved interests (empty until they choose). */
    @JvmStatic
    fun get(): List<String> {
        val ctx = Vinta.appContext ?: return emptyList()
        return prefs(ctx).getString(KEY, "").orEmpty().split(",").filter { it in OPTIONS }
    }

    /** Save interests from your own settings screen. Unknown ids are dropped. */
    @JvmStatic
    fun set(ids: List<String>): List<String> {
        val clean = ids.filter { it in OPTIONS }.distinct()
        Vinta.appContext?.let { prefs(it).edit().putString(KEY, clean.joinToString(",")).apply() }
        return clean
    }

    /** Ready-made "Choose your interests" dialog. [onDone] gets the saved list (unchanged on Cancel). */
    @JvmStatic
    @JvmOverloads
    fun showPicker(activity: Activity, onDone: ((List<String>) -> Unit)? = null) {
        runCatching {
            val ids = OPTIONS.keys.toList()
            val current = get()
            val checked = BooleanArray(ids.size) { ids[it] in current }
            AlertDialog.Builder(activity)
                .setTitle("Choose your interests")
                .setMultiChoiceItems(OPTIONS.values.toTypedArray(), checked) { _, i, on -> checked[i] = on }
                .setPositiveButton("Save") { _, _ -> onDone?.invoke(set(ids.filterIndexed { i, _ -> checked[i] })) }
                .setNegativeButton("Cancel") { _, _ -> onDone?.invoke(get()) }
                .setOnCancelListener { onDone?.invoke(get()) }
                .show()
        }.onFailure { onDone?.invoke(get()) }
    }
}
