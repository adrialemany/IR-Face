package android.service.trust;

/**
 * Stub de solo compilación de la API de sistema android.service.trust.TrustAgentService
 * (Android 14/15). En el teléfono se usa la clase real del framework.
 */
public class TrustAgentService extends android.app.Service {
    public static final int FLAG_GRANT_TRUST_INITIATED_BY_USER = 1;
    public static final int FLAG_GRANT_TRUST_DISMISS_KEYGUARD = 1 << 1;
    public static final int FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE = 1 << 2;
    public static final int FLAG_GRANT_TRUST_DISPLAY_MESSAGE = 1 << 3;
    public static final String SERVICE_INTERFACE = "android.service.trust.TrustAgentService";
    public static final String TRUST_AGENT_META_DATA = "android.service.trust.trustagent";

    public TrustAgentService() { throw new RuntimeException("Stub!"); }
    @Override public void onCreate() { throw new RuntimeException("Stub!"); }
    public void onUnlockAttempt(boolean successful) { throw new RuntimeException("Stub!"); }
    public void onUserRequestedUnlock(boolean dismissKeyguard) { throw new RuntimeException("Stub!"); }
    public void onUserMayRequestUnlock() { throw new RuntimeException("Stub!"); }
    public void onTrustTimeout() { throw new RuntimeException("Stub!"); }
    public void onDeviceLocked() { throw new RuntimeException("Stub!"); }
    public void onDeviceUnlocked() { throw new RuntimeException("Stub!"); }
    public void onDeviceUnlockLockout(long timeoutMs) { throw new RuntimeException("Stub!"); }
    public boolean onConfigure(java.util.List<android.os.PersistableBundle> options) { throw new RuntimeException("Stub!"); }
    public final void grantTrust(CharSequence message, long durationMs, int flags) { throw new RuntimeException("Stub!"); }
    public final void revokeTrust() { throw new RuntimeException("Stub!"); }
    public final void setManagingTrust(boolean managingTrust) { throw new RuntimeException("Stub!"); }
    public final void lockUser() { throw new RuntimeException("Stub!"); }
    public final void showKeyguardErrorMessage(CharSequence message) { throw new RuntimeException("Stub!"); }
    @Override public final android.os.IBinder onBind(android.content.Intent intent) { throw new RuntimeException("Stub!"); }
}
