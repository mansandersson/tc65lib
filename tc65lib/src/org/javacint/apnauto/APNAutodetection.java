package org.javacint.apnauto;

//#if sdkns == "siemens"
import com.siemens.icm.io.*;
//#elif sdkns == "cinterion"
//# import com.cinterion.io.*;
//#endif
import java.io.IOException;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import org.javacint.at.ATExecution;
import org.javacint.common.PropertiesFile;
import org.javacint.common.ResourceProvider;
import org.javacint.logging.Logger;
import org.javacint.settings.Settings;
import org.javacint.sms.SimpleSMS;

/**
 * APN Autodetection class.
 *
 * This class uses the Android APN list rewritten in an other (more compact)
 * format.
 *
 * The core method is autoLoadRightGPRSSettings
 *
 */
public final class APNAutodetection {

    private final Vector sources = new Vector();
    public static final boolean LOG = false;

    public APNAutodetection() {
    }

    public void addParametersSource(ResourceProvider source) {
        sources.addElement(source);
    }

    public void addDefaultParameters() {
        try {
            Vector names = getSettingsListNames();
            sources.addElement( // My list of APNS
                    new GPRSParametersResourceReader(
                    new ResourceProvider(GPRSParametersResourceReader.class, "/apns.txt"),
                    names));

            sources.addElement( // Android's list of APNS
                    new GPRSParametersResourceReader(
                    new ResourceProvider(GPRSParametersResourceReader.class, "/android.txt"),
                    names));
        } catch (IOException ex) {
            if (Logger.BUILD_CRITICAL) {
                Logger.log(this + ".addDefaultParameters", ex, true);
            }
        }
    }

    private Vector getSettingsListNames() {
        String mcc, mnc;
        {
            String[] monc = ATExecution.getMONC();
            mcc = monc[0];
            mnc = monc[1];
        }
        String operator = ATExecution.getCopsOperator().
                trim().
                toLowerCase().
                replace(' ', '_');
        if (Logger.BUILD_DEBUG && LOG) {
            Logger.log("Operator: " + operator);
            Logger.log("MCC: " + mcc);
            Logger.log("MNC: " + mnc);
        }


        Vector names = new Vector();
        // These are the different types of configuration that might have been defined
        names.addElement(mcc + "-" + operator); // covers "MVNO"
        names.addElement(mcc + "-" + mnc); // This is the most important one
        names.addElement(mcc); // This is the per-country one
        return names;
    }

    public String autoLoadRightGPRSSettings() {
        String apn = null;
        try { // Let's detect the IMSI
            String simDetected, simSaved;
            simDetected = ATExecution.getIccid();
            simSaved = Settings.get(Settings.SETTING_ICCID);

            if (simDetected == null) {
                if (Logger.BUILD_CRITICAL) {
                    Logger.log("No sim card detected !");
                }
            } else if (!simDetected.equals(simSaved)) {
                if (Logger.BUILD_WARNING) {
                    Logger.log("Sim card changed since last successful APN detection ! saved=" + simSaved + ", detected=" + simDetected);
                }

                { // First we try if we have any chance with the current APN (maybe it was defined in the console or by SMS)
                    String apnSetting = Settings.get(Settings.SETTING_APN);
                    if (apnSetting != null) {
                        if (Logger.BUILD_DEBUG) {
                            Logger.log(this + ".autoLoadRightGPRSSettings: Trying current APN setting...");
                        }
                        if (testApn(apnSetting, GPRSSettings.DEFAULT_TARGET)) {
                            apn = apnSetting;
                            if (Logger.BUILD_DEBUG) {
                                Logger.log(this + ".autoLoadRightGPRSSettings: Current APN setting is ok!");
                            }
                        }
                    }

                    int size = sources.size();
                    for (int i = 0; i < size && apn == null; i++) {
                        GPRSParametersProvider provider = (GPRSParametersProvider) sources.elementAt(i);
                        if (Logger.BUILD_DEBUG) {
                            Logger.log(this + ".autoLoadRightGPRSSettings: Trying APN parameters provider " + provider + " (" + (i + 1) + "/" + size + ")");
                        }
                        GPRSSettings set = testGPRSParameters(provider);
                        apn = set != null ? set.toSjnet() : null;
                    }
                }

                String phoneManager = Settings.get(Settings.SETTING_MANAGERSPHONE);

                if (apn != null) {
                    if (Logger.BUILD_NOTICE) {
                        Logger.log("New APN is : " + apn);
                    }

                    // We only save the IMSI if we found a good APN
                    // This renew APN detection until we have the right APN detected
                    // The main drawback is : We will detect APN at each startup
                    // even if network is working fine
                    Settings.set(Settings.SETTING_ICCID, simDetected);
                    Settings.set(Settings.SETTING_APN, apn);
                    Settings.save();

                    if (phoneManager != null) {
                        String imei = ATExecution.getImei();
                        SimpleSMS.send(phoneManager, "New detection!\nIMEI:" + imei + "\nICCID:" + simDetected + "\nAPN:" + apn + "\n");
                    }
                } else {
                    if (phoneManager != null) {
                        String mcc, mnc;
                        {
                            String[] monc = ATExecution.getMONC();
                            mcc = monc[0];
                            mnc = monc[1];
                        }
                        String imei = ATExecution.getImei();
                        SimpleSMS.send(phoneManager, "APN NOT FOUND!\nIMEI:" + imei + "\nICCID:" + simDetected + "\nOperator: " + ATExecution.getCopsOperator() + " (" + mcc + "-" + mnc + ")");
                    }
                }
            } else {
                apn = Settings.get(Settings.SETTING_APN);
                if (apn != null) {

                    if (Logger.BUILD_VERBOSE) {
                        Logger.log("We're going to load APN : " + apn);
                    }
                    ATExecution.applyAPN(apn);
                }
            }
        } catch (Exception ex) {
            if (Logger.BUILD_CRITICAL) {
                Logger.log("APNAutodetection.autoLoadRightGPRSSettings", ex);
            }
        }
        return apn;
    }

    private GPRSSettings testGPRSParameters(GPRSParametersProvider reader) throws ATCommandFailedException, IOException {
        if (Logger.BUILD_DEBUG && LOG) {
            Logger.log(this + ".testGPRSSettingsReader:" + reader);
        }

        for (GPRSSettings set; (set = reader.next()) != null;) {
            if (testApn(set)) {
                return set;
            }
        }
        return null;
    }

    private boolean testApn(GPRSSettings set) throws ATCommandFailedException {
        if (Logger.BUILD_DEBUG && LOG) {
            Logger.log(this + ".testApn: Testing " + set);
        }
        return testApn(set.toSjnet(), set.getTarget());
    }

    private boolean testApn(String apn, String target) throws ATCommandFailedException {
        if (Logger.BUILD_DEBUG) {
            Logger.log(this + ".testApn( \"" + apn + "\", \"" + target + "\" );");
        }
        ATExecution.applyAPN(apn);

        try {
            SocketConnection conn = (SocketConnection) Connector.open("socket://" + target);
            conn.close();
            if (Logger.BUILD_DEBUG) {
                Logger.log("Connected ok !");
            }
            return true;
        } catch (Exception ex) {
            if (Logger.BUILD_CRITICAL) {
                Logger.log("APNAutodetection: " + ex);
            }
        }
        return false;
    }

    public void applySettingsApn() {
        ATExecution.applyAPN(Settings.get(Settings.SETTING_APN));
    }

    public String toString() {
        return "APNAutodetection";
    }
}
