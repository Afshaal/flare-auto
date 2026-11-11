package app.flare.auto

class MediaAccess : android.service.notification.NotificationListenerService() {
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) { BannerNotifications.posted(sbn) }
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification) { BannerNotifications.removed(sbn.key) }
}
