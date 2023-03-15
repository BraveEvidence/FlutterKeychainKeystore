import UIKit
import Flutter

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
    
    let yourEdgeService = "flutapp.com"
    let accountName = "flutapp"
    
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        GeneratedPluginRegistrant.register(with: self)
        
        let controller : FlutterViewController = window?.rootViewController as! FlutterViewController
        
        let keychainChannel = FlutterMethodChannel(name:"keychainPlatform",
                                                   binaryMessenger: controller.binaryMessenger)
        
        keychainChannel.setMethodCallHandler({
            (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
            if call.method == "saveData" {
                if let args = call.arguments as? Dictionary<String, Any>, let data = args["data"] as? String {
                    self.savePassword(result: result,data: data)
                } else {
                    result(FlutterError.init(code: "errorSetDebug", message: "data or format error", details: nil))
                }
                
            } else if call.method == "getData" {
                self.getPassword(result: result)
            } else if call.method == "deleteData" {
                self.deletePassword(result: result)
            } else {
                result(FlutterMethodNotImplemented)
                return
            }
        })
        
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
    
    private func savePassword(result: FlutterResult,data: String){
        do {
            try save(service: yourEdgeService, account: accountName, password: data.data(using: .utf8) ?? Data())
            result(true)
        } catch {
            result(FlutterError.init(code: "errorSetDebug", message: "Failed to save password", details: nil))
        }
        
    }
    
    private func getPassword(result: FlutterResult){
        if let data = getData(service: yourEdgeService, account: accountName) {
            let password = String(decoding: data,as: UTF8.self)
            result(password)
        } else {
            result(FlutterError.init(code: "errorSetDebug", message: "Failed to read password", details: nil))
        }
    }
    
    private func deletePassword(result: FlutterResult){
        let secItemClasses = [
            kSecClassGenericPassword,
            kSecClassInternetPassword,
            kSecClassCertificate,
            kSecClassKey,
            kSecClassIdentity
        ]
        for itemClass in secItemClasses {
            let spec: NSDictionary = [kSecClass: itemClass]
            SecItemDelete(spec)
        }
        result(true)
    }
    
    enum KeychainError: Error {
        case duplicateEntry
        case unknown(OSStatus)
    }
    
    func save(
        service: String,account: String,password: Data
    ) throws {
        let query: [String: AnyObject] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service as AnyObject,
            kSecAttrAccount as String: account as AnyObject,
            kSecValueData as String: password as AnyObject
        ]
        
        let status = SecItemAdd(query as CFDictionary, nil)
        
        guard status != errSecDuplicateItem else {
            throw KeychainError.duplicateEntry
        }
        
        guard status == errSecSuccess else {
            throw KeychainError.unknown(status)
        }
    }
    
    func getData(service: String,account: String) -> Data? {
        let query: [String: AnyObject] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service as AnyObject,
            kSecAttrAccount as String: account as AnyObject,
            kSecReturnData as String: kCFBooleanTrue,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        SecItemCopyMatching(query as CFDictionary, &result)
        
        return result as? Data
    }
}
