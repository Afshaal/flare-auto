package app.flare.auto

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout

class ProfilesActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state);render()
    }
    private fun render(reveal:String?=null) {
        val ui=UiKit(this)
        window.immersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes=window.attributes.apply { dimAmount=.72f }
        window.setBackgroundDrawable(GradientDrawable().apply { setColor(ui.t.card); cornerRadius=ui.dp(24).toFloat() })
        val root=ui.column().apply { setPadding(ui.dp(28),ui.dp(24),ui.dp(28),ui.dp(20)) }
        root.addView(ui.text("Who’s driving?",28,ui.t.textPrimary).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(MATCH,ui.dp(52)))
        val choices=LinearLayout(this).apply { gravity=Gravity.CENTER }
        val avatarSize=minOf(ui.dp(128),(resources.displayMetrics.heightPixels*.22f).toInt())
        val scroll=android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        var revealTile:View?=null
        fun choice(driver:ProfileStore.Driver,action:()->Unit) {
            val name=driver.name;val avatar=if(driver.id=="guest") 1 else driver.avatar;val active=ProfileStore.active(this)==driver.id
            val tile=ui.column().apply { gravity=Gravity.CENTER; setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(8)); setOnClickListener { action() } }
            tile.addView(ProfileAvatar(this,avatar,if(active) ui.t.accent else ui.t.textSecondary),LinearLayout.LayoutParams(avatarSize,avatarSize))
            tile.addView(ui.text(name,18,ui.t.textPrimary).apply { gravity=Gravity.CENTER; maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(avatarSize+ui.dp(24),ui.dp(40)))
            if(driver.id!="default" && driver.id!="guest") tile.setOnLongClickListener { manageProfile(driver);true }
            if(driver.id==reveal) revealTile=tile
            choices.addView(tile)
        }
        ProfileStore.drivers(this).forEach { driver ->
            choice(driver) {
                ProfileStore.select(this,driver.id)
                startActivity(Intent(this,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish()
            }
        }
        val add=ui.text("+\nAdd New",24,ui.t.textSecondary).apply { gravity=Gravity.CENTER; setOnClickListener { createProfile(ui) } }
        choices.addView(add,LinearLayout.LayoutParams(avatarSize+ui.dp(32),avatarSize+ui.dp(40)))
        scroll.addView(choices)
        root.addView(scroll,LinearLayout.LayoutParams(MATCH,WRAP))
        revealTile?.let { tile->scroll.post { scroll.smoothScrollTo(tile.left,0) } }
        root.addView(ui.text("Hold a custom profile to edit or delete it",12,ui.t.textSecondary).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(MATCH,ui.dp(32)))
        root.addView(ui.text("Startup selection",14,ui.t.textSecondary).apply {
            gravity=Gravity.CENTER; minHeight=ui.dp(40)
            setOnClickListener { ThemedDialogs.Builder(this@ProfilesActivity).setTitle("Startup selection")
                .setSingleChoiceItems(arrayOf("Ask who’s driving","Last profile","Family"),arrayOf("ask","last","default").indexOf(ProfileStore.startupMode(this@ProfilesActivity))) { _,i ->
                    ProfileStore.startupMode(this@ProfilesActivity,arrayOf("ask","last","default")[i]); ProfileStore.defaultDriver(this@ProfilesActivity,"default")
                }.show() }
        })
        setContentView(root)
        window.setLayout(minOf(ui.dp(620),(resources.displayMetrics.widthPixels*.86f).toInt()),-2)
        window.setGravity(Gravity.CENTER)
        setFinishOnTouchOutside(true)
    }
    private fun manageProfile(driver:ProfileStore.Driver) {
        ThemedDialogs.Builder(this).setTitle(driver.name).setItems(arrayOf("Rename / change avatar","Delete profile")) { _,i->
            if(i==0) createProfile(UiKit(this),driver)
            else ThemedDialogs.Builder(this).setTitle("Delete ${driver.name}?").setMessage("Delete this profile, its layout and custom images? This cannot be undone. Family and Guest will be kept.")
                .setPositiveButton("Delete") { _,_->ProfileStore.delete(this,driver.id);render() }.setNegativeButton("Cancel",null).show()
        }.show()
    }
    private fun createProfile(ui:UiKit,driver:ProfileStore.Driver?=null) {
        var selected=driver?.avatar ?: 0
        val body=ui.column().apply { setPadding(ui.dp(18),ui.dp(8),ui.dp(18),ui.dp(8)) }
        val input=android.widget.EditText(this).apply { hint="Profile name";setText(driver?.name.orEmpty()); setSingleLine(); setTextColor(ui.t.textPrimary); filters=arrayOf(android.text.InputFilter.LengthFilter(32)) }
        body.addView(input)
        val icons=LinearLayout(this);body.addView(icons)
        val views=ArrayList<View>()
        for(i in 0..5) {
            val avatar=ProfileAvatar(this,i,ui.t.textPrimary).apply { contentDescription=arrayOf("Person","Steering wheel","Planet","Mountains","Sun","Car")[i];alpha=if(i==selected) 1f else .4f; setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8)) }
            avatar.setOnClickListener { selected=i;views.forEachIndexed { n,v->v.alpha=if(n==i) 1f else .4f } }
            views.add(avatar);icons.addView(avatar,LinearLayout.LayoutParams(0,ui.dp(64),1f))
        }
        val dialog=ThemedDialogs.Builder(this).setTitle(if(driver==null) "Create profile" else "Edit profile").setView(body).setPositiveButton(if(driver==null) "Create" else "Save",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.t.card))
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setTextColor(ui.t.accent)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(ui.t.accent)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name=input.text.toString().trim()
            if(name.isEmpty()) input.error="Enter a profile name" else {
                if(driver==null) ProfileStore.add(this,name,selected) else ProfileStore.update(this,driver.id,name,selected)
                dialog.dismiss();render(driver?.id ?: ProfileStore.drivers(this).last().id)
            }
        } };dialog.show()
    }
}
