package org.smartregister.chw.mothermentor.util;

import static org.smartregister.util.Utils.getAllSharedPreferences;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import org.json.JSONObject;
import org.opensrp.api.constants.Gender;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorCallDialogContract;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.repository.BaseRepository;
import org.smartregister.sync.ClientProcessorForJava;
import org.smartregister.sync.helper.ECSyncHelper;
import org.smartregister.util.PermissionUtils;

import java.util.Date;

import timber.log.Timber;

public class MotherMentorUtil {

    public static void processEvent(AllSharedPreferences allSharedPreferences, Event baseEvent) throws Exception {
        if (baseEvent != null) {
            MotherMentorJsonFormUtils.tagEvent(allSharedPreferences, baseEvent);
            JSONObject eventJson = new JSONObject(MotherMentorJsonFormUtils.gson.toJson(baseEvent));

            getSyncHelper().addEvent(baseEvent.getBaseEntityId(), eventJson, BaseRepository.TYPE_Unprocessed);
            startClientProcessing();
        }
    }

    public static void startClientProcessing() {
        try {
            long lastSyncTimeStamp = getAllSharedPreferences().fetchLastUpdatedAtDate(0);
            Date lastSyncDate = new Date(lastSyncTimeStamp);
            getClientProcessorForJava().processClient(getSyncHelper().getEvents(lastSyncDate, BaseRepository.TYPE_Unprocessed));
            getAllSharedPreferences().saveLastUpdatedAtDate(lastSyncDate.getTime());
        } catch (Exception e) {
            Timber.d(e);
        }
    }

    public static ECSyncHelper getSyncHelper() {
        return MotherMentorLibrary.getInstance().getEcSyncHelper();
    }

    public static ClientProcessorForJava getClientProcessorForJava() {
        return MotherMentorLibrary.getInstance().getClientProcessorForJava();
    }

    public static Spanned fromHtml(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(text);
        }
    }

    public static boolean launchDialer(final Activity activity, final BaseMotherMentorCallDialogContract.View callView, final String phoneNumber) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

            // set a pending call execution request
            if (callView != null) {
                callView.setPendingCallRequest(() -> MotherMentorUtil.launchDialer(activity, callView, phoneNumber));
            }

            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, PermissionUtils.PHONE_STATE_PERMISSION_REQUEST_CODE);

            return false;
        } else {
            if (((TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number()
                    == null) {

                Timber.i("No dial application so we launch copy to clipboard...");

                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(activity.getText(R.string.copied_phone_number), phoneNumber);
                clipboard.setPrimaryClip(clip);

                CopyToClipboardDialog copyToClipboardDialog = new CopyToClipboardDialog(activity, R.style.copy_clipboard_dialog);
                copyToClipboardDialog.setContent(phoneNumber);
                copyToClipboardDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                copyToClipboardDialog.show();
                Toast.makeText(activity, activity.getText(R.string.copied_phone_number), Toast.LENGTH_SHORT).show();

            } else {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null));
                activity.startActivity(intent);
            }
            return true;
        }
    }

    public static void saveFormEvent(final String jsonString) throws Exception {
        AllSharedPreferences allSharedPreferences = MotherMentorLibrary.getInstance().context().allSharedPreferences();
        Event baseEvent = MotherMentorJsonFormUtils.processJsonForm(allSharedPreferences, jsonString);
        MotherMentorUtil.processEvent(allSharedPreferences, baseEvent);
    }

    public static int getMemberProfileImageResourceIdentifier(String entityType) {
        return R.mipmap.ic_member;
    }

    public static String getGenderTranslated(Context context, String gender) {
        if (gender.equalsIgnoreCase(Gender.MALE.toString())) {
            return context.getResources().getString(R.string.male);
        } else if (gender.equalsIgnoreCase(Gender.FEMALE.toString())) {
            return context.getResources().getString(R.string.female);
        }
        return "";
    }

    protected static Event getCloseMotherMentorEvent(String jsonString,
                                             String baseEntityId) {

        Event closeMotherMentorEvent = new Gson().
                fromJson(jsonString, Event.class);

        closeMotherMentorEvent.setEntityType(Constants.TABLES.MOTHERMENTOR_SCREENING);
        closeMotherMentorEvent.setEventType(Constants.EVENT_TYPE.CLOSE_MOTHER_MENTOR_SERVICE);
        closeMotherMentorEvent.setBaseEntityId(baseEntityId);
        closeMotherMentorEvent.setFormSubmissionId(JsonFormUtils.
                generateRandomUUIDString());
        closeMotherMentorEvent.setEventDate(new Date());
        return closeMotherMentorEvent;
    }

    public static void closeMotherMentorService(String baseEntityId) {
        AllSharedPreferences allSharedPreferences = MotherMentorLibrary.
                getInstance().
                context().
                allSharedPreferences();
        Event closeMotherMentorEvent = getCloseMotherMentorEvent(new JSONObject().
                toString(),
                baseEntityId);

        try {
            NCUtils.addEvent(allSharedPreferences, closeMotherMentorEvent);
            NCUtils.startClientProcessing();
        } catch (Exception e) {
            Timber.e(e);
        }
    }
}

