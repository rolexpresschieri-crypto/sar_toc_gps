import ComposeApp
import SwiftUI
import UIKit
import UserNotifications

private let tocPushNotificationId = "toc_sar_toc_push"

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        TocPushIosBridge.shared.install(
            show: { title, body in
                let content = UNMutableNotificationContent()
                content.title = title
                content.body = body
                content.sound = .default
                let request = UNNotificationRequest(
                    identifier: tocPushNotificationId,
                    content: content,
                    trigger: nil
                )
                UNUserNotificationCenter.current().add(request)
            },
            clear: {
                let center = UNUserNotificationCenter.current()
                center.removeDeliveredNotifications(withIdentifiers: [tocPushNotificationId])
                center.removePendingNotificationRequests(withIdentifiers: [tocPushNotificationId])
            }
        )
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound]) { _, _ in }
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
