package com.habitrpg.android.habitica.ui.fragments.social

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.components.AppComponent
import com.habitrpg.android.habitica.data.UserRepository
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.models.user.User
import com.habitrpg.android.habitica.modules.AppModule
import com.habitrpg.android.habitica.ui.fragments.BaseFragment

import javax.inject.Inject
import javax.inject.Named

import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.habitrpg.android.habitica.data.InventoryRepository
import com.habitrpg.android.habitica.data.SocialRepository
import com.habitrpg.android.habitica.events.commands.OpenMenuItemCommand
import com.habitrpg.android.habitica.extensions.backgroundCompat
import com.habitrpg.android.habitica.extensions.layoutInflater
import com.habitrpg.android.habitica.extensions.notNull
import com.habitrpg.android.habitica.helpers.RemoteConfigManager
import com.habitrpg.android.habitica.models.inventory.Quest
import com.habitrpg.android.habitica.models.inventory.QuestContent
import com.habitrpg.android.habitica.models.members.PlayerTier
import com.habitrpg.android.habitica.models.social.Group
import com.habitrpg.android.habitica.ui.fragments.NavigationDrawerFragment
import com.habitrpg.android.habitica.ui.helpers.DataBindingUtils
import com.habitrpg.android.habitica.ui.views.HabiticaAlertDialog
import com.habitrpg.android.habitica.ui.views.social.UsernameLabel
import kotlinx.android.synthetic.main.shop_header.*
import kotlinx.android.synthetic.main.fragment_tavern_detail.*
import org.greenrobot.eventbus.EventBus
import org.w3c.dom.Text
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1

class TavernDetailFragment : BaseFragment() {

    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var socialRepository: SocialRepository
    @Inject
    lateinit var inventoryRepository: InventoryRepository
    @field:[Inject Named(AppModule.NAMED_USER_ID)]
    lateinit var userId: String
    @Inject
    lateinit var configManager: RemoteConfigManager

    private var shopSpriteSuffix = ""

    private var user: User? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_tavern_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shopSpriteSuffix = configManager.shopSpriteSuffix()

        compositeSubscription.add(userRepository.getUser(userId).subscribe(Action1 {
            this.user = it
            this.updatePausedState()
        }, RxErrorHandler.handleEmptyError()))

        descriptionView.setText(R.string.tavern_description)
        namePlate.setText(R.string.tavern_owner)

        npcBannerView.shopSpriteSuffix = configManager.shopSpriteSuffix()
        npcBannerView.identifier = "tavern"

        addPlayerTiers()
        bindButtons()

        compositeSubscription.add(socialRepository.getGroup(Group.TAVERN_ID)
                .doOnNext({  if (!it.hasActiveQuest) worldBossSection.visibility = View.GONE })
                .filter { it.hasActiveQuest }
                .doOnNext({ questProgressView.progress = it.quest})
                .flatMap { inventoryRepository.getQuestContent(it.quest?.key).first() }
                .subscribe(Action1 {
                    questProgressView.quest = it
                    worldBossSection.visibility = View.VISIBLE
                }, RxErrorHandler.handleEmptyError()))

        compositeSubscription.add(socialRepository.getGroup(Group.TAVERN_ID)
                .filter { it.hasActiveQuest }
                .doOnNext { descriptionView.setText(R.string.tavern_description_world_boss) }
                .filter { it.quest?.rageStrikes?.any { it.key == "tavern" } }
                .filter { it.quest?.rageStrikes?.filter { it.key == "tavern" }?.get(0)?.wasHit == true }
                .subscribe(Action1 {
                    val key = it.quest?.key
                    if (key != null) {
                        shopSpriteSuffix = key
                    }
                }, RxErrorHandler.handleEmptyError()))

        socialRepository.retrieveGroup(Group.TAVERN_ID).subscribe(Action1 { }, RxErrorHandler.handleEmptyError())

        user.notNull { questProgressView.configure(it) }
    }

    override fun onDestroy() {
        userRepository.close()
        socialRepository.close()
        inventoryRepository.close()
        super.onDestroy()
    }

    private fun bindButtons() {
        innButton.setOnClickListener {
            user?.notNull { userRepository.sleep(it).subscribe(Action1 { }, RxErrorHandler.handleEmptyError()) }
        }
        guidelinesButton.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse("https://habitica.com/static/community-guidelines")
            context?.startActivity(i)
        }
        faqButton.setOnClickListener {
            EventBus.getDefault().post(OpenMenuItemCommand(NavigationDrawerFragment.SIDEBAR_HELP))

        }
        reportButton.setOnClickListener {
            EventBus.getDefault().post(OpenMenuItemCommand(NavigationDrawerFragment.SIDEBAR_ABOUT))
        }

        worldBossSection.infoIconView.setOnClickListener {
            val context = this.context
            val quest = questProgressView.quest
            if (context != null && quest != null) {
                showWorldBossInfoDialog(context, quest)
            }
        }
    }


    private fun updatePausedState() {
        if (innButton == null) {
            return
        }
        if (user?.preferences?.sleep == true) {
            innButton .setText(R.string.tavern_inn_checkOut)
        } else {
            innButton.setText(R.string.tavern_inn_rest)
        }
    }

    private fun addPlayerTiers() {
        for (tier in PlayerTier.getTiers()) {
            val container = FrameLayout(context)
            context.notNull {
                container.backgroundCompat = ContextCompat.getDrawable(it, R.drawable.layout_rounded_bg_gray_700)
            }
            val label = UsernameLabel(context, null)
            label.tier = tier.id
            label.username = tier.title
            val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER)
            container.addView(label, params)
            playerTiersView.addView(container)
            val padding = context?.resources?.getDimension(R.dimen.spacing_medium)?.toInt() ?: 0
            container.setPadding(0, padding, 0, padding)
        }
        playerTiersView.invalidate()
    }

    override fun injectFragment(component: AppComponent) {
        component.inject(this)
    }

    companion object {

        fun showWorldBossInfoDialog(context: Context, quest: QuestContent) {
            val alert = HabiticaAlertDialog(context)
            val bossName = quest.boss.name ?: ""
            alert.setTitle(R.string.world_boss_description_title)
            alert.setTitleBackgroundColor(quest.colors?.lightColor ?: 0)
            alert.setSubtitle(context.getString(R.string.world_boss_description_subtitle, bossName))
            alert.setAdditionalContentView(R.layout.world_boss_description_view)

            val descriptionView = alert.getContentView()
            val promptView: TextView? = descriptionView?.findViewById(R.id.worldBossActionPromptView)
            promptView?.text = context.getString(R.string.world_boss_action_prompt, bossName)
            promptView?.setTextColor(quest.colors?.lightColor ?: 0)
            val background = ContextCompat.getDrawable(context, R.drawable.rounded_border)
            background?.setColorFilter(quest.colors?.extraLightColor ?: 0, PorterDuff.Mode.MULTIPLY)
            promptView?.backgroundCompat = background

            alert.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.close), { dialog, _ ->
                dialog.dismiss()
            })
            alert.show() }
    }
}