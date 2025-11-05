# iOS Multi-Process Architecture

iOS has always been very strict about running processes in the background. The motivation is obvious: if they let processes run in the background whenever they want, then that's less CPU and memory for the foreground process (what the user is actually doing), plus reduced battery life. But not allowing any background processing was overly restrictive. So over the years they have slowly made exceptions and workarounds to allow for specific (Apple-approved) use cases.



What does this mean for Phoenix ?

* Apple does NOT allow the main Phoenix app to run in response to a push notification
* Apple does allow us to create a separate mini-process. This is like a mini version of Phoenix with no UI. And iOS will launch this process in response to a received push notification (under certain conditions)
* The mini process has limited memory: it is capped at 24 MB. If the mini process exceeds this amount, iOS will kill it.
* The mini process is given a maximum of 30 seconds to run. If it exceeds this amount, iOS will kill it.



So on iOS there are 2 separate processes:

- mainApp (which is the UI app)
- notifySrvExt (our mini app)



The mini app is a [notification service extension](https://developer.apple.com/documentation/usernotifications/unnotificationserviceextension), which is one of those Apple-approved exceptions. It was designed for secure messaging apps, where the server cannot read the content of an incoming message. So, in order for Whatsapp/Signal/etc to display the message:

* a push notification is sent to the device, but it contains encrypted data
* only the client-side app is able to decrypt this data
* so iOS is willing to launch a "notification service extension" (mini-app), which is capable of decrypting the message, and updating the text of the push notification with the decrypted message



This isn't exactly our use case. But it's currently the best option we have.



*Note: there are other options available, but they're less ideal. For example, with an alternative solution, iOS might drop or delay the push notification if the phone is in low-battery mode. This would result in a missed payment. We specifically switched to the notification service extension solution because, after investigating all the options, it was determined that this is the best solution for now.* 



So in general, here's how it works:

- when a push notification arrives for Phoenix, iOS asks the question "is the mainApp open AND in the foreground"
- if the answer is YES, then the push notification is handed to the mainApp for processing
- if the answer is NO, then the notifySrvExt process is launched, and is handed the push notification for processing
- the notifySrvExt is granted a maximum of 30 seconds to finish running, or it will be killed automatically by iOS



This means that most of the time, the 2 processes are not running at the same time. But there are edge cases:



- the user might launch the mainApp while the notifySrvExt is running
- the user might bring the mainApp into the foreground while the notifySrvExt is running (i.e. mainApp was already open, it was just in the background when push notification arrived)



We know that if both processes are running at the same time (without any kind of guardrails), then they can interfere with each other (e.g. cause disconnects and/or force-close channels.) However, we've spent several years working around a variety of edge cases. Such that the process of receiving payments on iOS works very well now, regardless of whether the mainApp is in the foreground, background or not running.



### Guardrail #1

The user can background the app at anytime. Including in the middle of sending or receiving a payment.



*In fact, if you watch an average user, they commonly hit the Send button on a payment, and then immediately background the app or lock the phone. This is what they commonly do when sending messages in Whatsapp, for example. And it doesn't cause issues there. So why should they have to wait for the payment to complete when there are important Instagram posts to read.*



Luckily, iOS gives us tools we can use to properly deal with these situations. You can tell iOS that you're in the middle of an "important task", and they will give your app up to 3 additional minutes to finish that task before app goes into "standard background mode". So for example:

- user hits Send button to start a payment
- we tell iOS that we've started a "long lived task"
- user backgrounds Phoenix app
- iOS knows we're in the middle of some important task, so it allows us to continue working (i.e. doesn't automatically close network connections, allows us to continue using CPU, etc)
- when the payment finishes, we tell iOS that we've finished our "long lived task", and iOS moves us into full/standard background mode (i.e. kills open network connections)



We use this technique for outgoing payments.



In lightning-kmp there is also the concept of a "SensitiveTaskEvent", which is triggered for incoming payments, as well as other tasks such as incoming splices. When a `SensitiveTaskEvent.TaskStarted` fires, we also start a "long lived task" that will keep the mainApp's connection open. 



And as long as the mainApp's connection is open, the server won't send a push notification, and the notifySrvExt process won't launch.



### Guardrail #2

The notifySrvExt process knows when the mainApp is active, and vice-versa. (This is achieved thru a simple XPC channel.)  Now recall our edge cases:



- the user might launch the mainApp while the notifySrvExt is running
- the user might bring the mainApp into the foreground while the notifySrvExt is running (i.e. mainApp was already open, it was just in the background when push notification arrived)



So if the notifySrvExt process is running, and it receives an XPC notification saying the mainApp is now active (i.e. one of the edge cases listed above), then the notifySrvExt process will abort, UNLESS it's already connected to the peer (because in this case, it might have already started processing the incoming payment).



### Guardrail #3

When the notifySrvExt process connects to the peer, it will continually (every 2 seconds) update a flag/timestamp in the shared UserDefaults system. The mainApp checks this flag/timestamp, and when it detects the timestamp is recent (less than 5 seconds ago), it will prevent the peer from connecting.



*While the mainApp is in this waiting process, it will display a message in the "connection status" saying "receiving payment in background".*



When the notifySrvExt process completes, it will stop updating the flag/timestamp. And once the timestamp is more than 5 seconds old, the mainApp will allow the peer to connect as usual.



*The architecture is durable because, if the notifySrvExt process crashes, this also means it stops updating the flag/timestamp. Allowing the mainApp to continue eventually.*



### Guardrail #4

We use [peer storage](https://github.com/ACINQ/lightning-kmp/pull/723) to store & restore channel backups. So:



* mainApp launches with channel state X, and then goes into background
* notifySrvExt launches, receives payment, and updates to channel state X+1
* mainApp goes into foreground, and re-establishes peer connection
* during this process, it automatically updates to channel state to X+1 using the peer's channel backup



### Known Issues

In lightning-kmp, the `IncomingPaymentHandler` stores a list of pending payments in memory (i.e. uncompleted multipart payments). This could result in the mainApp & notifySrvExt having different views of a pending payment, which could lead to missed/rejected payments.



On iOS we could solve this issue by using the shared container. That is, since the mainApp & notifySrvExt share a filesystem folder, we could essentially create shared memory between the two systems. This is on our TODO list...



