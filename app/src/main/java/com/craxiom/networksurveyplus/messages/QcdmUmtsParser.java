package com.craxiom.networksurveyplus.messages;

import android.location.Location;

import com.craxiom.messaging.LteNas;
import com.craxiom.messaging.LteNasChannelType;
import com.craxiom.messaging.LteNasData;
import com.craxiom.networksurveyplus.BuildConfig;
import com.google.protobuf.ByteString;

import java.time.ZonedDateTime;
import java.util.Arrays;

import timber.log.Timber;

import static com.craxiom.networksurveyplus.messages.QcdmConstants.LOG_LTE_NAS_EMM_OTA_IN_MSG;
import static com.craxiom.networksurveyplus.messages.QcdmConstants.LOG_LTE_NAS_EMM_OTA_OUT_MSG;
import static com.craxiom.networksurveyplus.messages.QcdmConstants.LOG_LTE_NAS_ESM_OTA_IN_MSG;
import static com.craxiom.networksurveyplus.messages.QcdmConstants.LOG_LTE_NAS_ESM_OTA_OUT_MSG;

/**
 * Contains parser methods for converting the QCDM WCDMA messages to various formats, like pcap records or protobuf
 * objects.
 *
 * @since 0.2.0
 */
public class QcdmUmtsParser
{
    private QcdmUmtsParser()
    {
    }

    /**
     * Given a {@link QcdmMessage} that contains a UMTS NAS OTA message {@link QcdmConstants#UMTS_NAS_OTA},
     * convert it to a pcap record byte array that can be consumed by tools like Wireshark.
     * <p>
     * The structure for the message:
     * ************************************************
     * | Direction (UL/DL) | Message Length | Payload |
     * |       1 byte      |     4 bytes    |    n    |
     * ************************************************
     *
     * @param qcdmMessage The QCDM message to convert into a pcap record.
     * @param location    The location to tie to the QCDM message when writing it to a pcap file. If null then no
     *                    location will be added to the PPI header.
     * @return The pcap record byte array to write to a pcap file, or null if the message could not be parsed.
     */
    public static byte[] convertUmtsNasOta(QcdmMessage qcdmMessage, Location location)
    {
        Timber.v("Handling a UMTS NAS OTA message");
        return convertUmtsNasOta(qcdmMessage, location, false, qcdmMessage.getSimId());
    }

    /**
     * Given a {@link QcdmMessage} that contains a UMTS NAS OTA message {@link QcdmConstants#UMTS_NAS_OTA_DSDS},
     * convert it to a pcap record byte array that can be consumed by tools like Wireshark.
     * <p>
     * The structure for the message:
     * **********************************************************
     * | SIM ID | Direction (UL/DL) | Message Length | Payload |
     * | 1 byte |       1 byte      |     4 bytes    |    n    |
     * **********************************************************
     * <p>
     * Note that this is very similar to the {@link #convertUmtsNasOta} method, except that this version supports Dual
     * SIM Dual Standby (DSDS).
     *
     * @param qcdmMessage The QCDM message to convert into a pcap record.
     * @param location    The location to tie to the QCDM message when writing it to a pcap file. If null then no
     *                    location will be added to the PPI header.
     * @return The pcap record byte array to write to a pcap file, or null if the message could not be parsed.
     */
    public static byte[] convertUmtsNasOtaDsds(QcdmMessage qcdmMessage, Location location)
    {
        Timber.v("Handling a UMTS NAS OTA DSDS message");

        final byte[] logPayload = qcdmMessage.getLogPayload();
        final int simId = logPayload[0] & 0xFF;

        return convertUmtsNasOta(qcdmMessage, location, true, simId);
    }

    /**
     * Helper method for parsing the two variations of UMTS NAS OTA messages.
     *
     * @param qcdmMessage The QCDM message to convert into a pcap record.
     * @param location    The location to tie to the QCDM message when writing it to a pcap file. If null then no
     *                    location will be added to the PPI header.
     * @param isDsds      True if the message is for DSDS, false otherwise.
     * @param simId       The SIM ID (aka Radio ID) associated with this message.
     * @return
     */
    private static byte[] convertUmtsNasOta(QcdmMessage qcdmMessage, Location location, boolean isDsds, int simId)
    {
        Timber.v("Handling a UMTS NAS OTA message");

        int startByte = isDsds ? 1 : 0;

        final byte[] logPayload = qcdmMessage.getLogPayload();

        final boolean isUplink = (logPayload[startByte] & 0xFF) == 1;

        final byte[] nasMessage = Arrays.copyOfRange(logPayload, 5 + startByte, logPayload.length);

        return PcapUtils.getGsmtapPcapRecord(GsmtapConstants.GSMTAP_TYPE_ABIS, nasMessage, 0, 0,
                isUplink, 0, 0, simId, location);
    }

    public static UmtsNas convertUmtsNasMessage(QcdmMessage qcdmMessage, Location location, String deviceId, String missionId, String mqttClientId)
    {
        Timber.v("Handling an UMTS NAS message");

        final byte[] logPayload = qcdmMessage.getLogPayload();
        final int simId = logPayload[0] & 0xFF;

        //return convertUmtsNasOta(qcdmMessage, location, true, simId);

        final UmtsNasData.Builder umtsNasDataBuilder = UmtsNasData.newBuilder();

        umtsNasDataBuilder.setDeviceSerialNumber(deviceId);
        if (mqttClientId != null) umtsNasDataBuilder.setDeviceName(mqttClientId);
        umtsNasDataBuilder.setMissionId(missionId);
        umtsNasDataBuilder.setDeviceTime(getRfc3339String(ZonedDateTime.now()));
        umtsNasDataBuilder.setAltitude((float) location.getAltitude());
        umtsNasDataBuilder.setLatitude(location.getLatitude());
        umtsNasDataBuilder.setLongitude(location.getLongitude());
        umtsNasDataBuilder.setRawMessage(ByteString.copyFrom(qcdmMessage.getLogPayload()));

        final UmtsNas.Builder UmtsNasBuilder = UmtsNas.newBuilder();
        UmtsNasBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        UmtsNasBuilder.setMessageType(UMTS_NAS_OTA);
        UmtsNasBuilder.setData(UmtsNasDataBuilder.build());

        return UmtsNasBuilder.build();
    }
}
