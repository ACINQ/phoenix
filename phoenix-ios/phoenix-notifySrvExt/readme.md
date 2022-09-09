# Architecture Notes: Notification Service Extension

In a previous version of Phoenix we used "silent" push notifications to wake up the phoenix app, and allow it to receive payments when in the background. However, there were multiple problems with this.



Apple regards "silent" push notifications as optional. For example, a notification that informs the app about new data on the server, which allows the app to pre-download the content. Therefore the app is "fresh" when the user opens it.



And with this very specific, very limited use-case in mind, Apple designed around it. So this means that silent push notifications don't get delivered if:

- the device is in low power mode
- the device has high CPU elsewhere
- the OS doesn't think the user will launch your app soon
- the OS doesn't feel like it today



So eventually, long after Apple had properly sabotaged all the encrypted messaging apps, it finally gave us the notification service extension. This is an app extension that will run in response to a push notification, allowing the app extension to run some code, and then modify the contents of the push notification before displaying it to the user.



Note: App extensions run as a **separate process**.



As per the docs for `UNNotificationServiceExtension`, iOS will launch our notification-service-extension when it receives a push notification in which:

> The remote notification’s aps dictionary includes the mutable-content key with the value set to 1.



---

## Frequently Asked Questions



**Q**: What happens if the main app is running when a push notification arrives ?



iOS will *still* launch the notification-service-extension. Further, it will allow the app extension to process the push notification, and will wait until the app extension invokes its `contentHandler` *before* delivering the push notificaiton to the container app.



Furthermore, the push notification delivered to the container app is the *original* push notification. (Excluding any modifications that may have been made by the app extension.)



Also, the final push notification payload emitted by the notification-service-extension is **NOT** displayed to the user.



**Q**: What happens if multiple push notifications arrive ?



iOS will launch the notification-service-extension upon receiving the first push notification. Subsequent push notifications are queued by the OS. After the app extension finishes processing the first notification (by invoking the `contentHandler`), then iOS will:

- display the first push notification
- dealloc the `UNNotificationServiceExtension`
- Initialize a new `UNNotificationServiceExtension` instance
- And invoke it's `didReceive(_:)` function with the next item in the queue



Note that it does not create a new app extension process. It re-uses the existing process, and launches a new `UNNotificationServiceExtension` within it.



---

## Cross Process Communication



We don't want the notification-service-extension to interfere with the main app. And if both processes are attempting to connect to the server, and accept the incoming payment, then they will interfere with each other.



We have 2 mechanisms in place to prevent this:

First, the ACINQ server only sends the push notification if there's not a connected node/client.

Second, we use mach ports to send ping/pong messages between the container app & app extension. This allows the app extension to yield to the main phoenix app, and allows it to handle the payment.



However, mach ports have some interesting attributes that should be explained.



First, after you `notify_register_dispatch` , iOS will apparently wake-up your container app (if it's in the background) to allow it to respond to an incoming mach message. This was really surprising to me. So that means the container app needs to explicitly `notify_suspend` & `notify_resume` when it going into background/foreground.



Second, mach channels are available to any proecess running on iOS. So if we hard-code the channel name, then we're opening the door to denial-of-service attacks by other apps. So this problem needs to be addressed by adding a secret UUID as part of the channel name.