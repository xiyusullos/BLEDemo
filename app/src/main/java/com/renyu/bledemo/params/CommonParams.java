package com.renyu.bledemo.params;

import com.renyu.bledemo.utils.DataUtils;
import com.renyu.blelibrary.ble.BLEFramework;
import com.renyu.blelibrary.utils.HexUtil;

import java.util.UUID;

/**
 * Created by renyu on 2017/2/3.
 */

public class CommonParams {

    public static final UUID UUID_SERVICE=UUID.fromString("0a2be667-2416-4373-b583-1147d905e39f");
    public static final UUID UUID_Characteristic=UUID.fromString("0000cdd2-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_DESCRIPTOR=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final int ERROR_RESP=0xf1;
    public static final int SET_SN_REQ=0xa7;
    public static final int SET_SN_RESP=0xa8;
    public static final int SET_MAGIC_REQ=0xa9;
    public static final int SET_MAGIC_RESP=0xaa;
    public static final int GET_DEVICE_CURRENT_REQ=0x9c;

    public static void setSNReq(DataUtils dataUtils, BLEFramework bleFramework, String sn) {
        char[] chars=HexUtil.encodeHex(sn.getBytes());
        byte[] bytes=new byte[chars.length/2+2];
        bytes[0]= (byte) SET_SN_REQ;
        bytes[1]=1;
        for (int i = 0; i < chars.length/2; i++) {
            bytes[i+2]=HexUtil.uniteBytes((byte) (chars[i*2] & 0xFF), (byte) (chars[i*2+1] & 0xFF));
        }
        dataUtils.addCommand(bleFramework, (byte) SET_SN_REQ, bytes);
    }

    public static void readSNReq(DataUtils dataUtils, BLEFramework bleFramework) {
        byte[] bytes=new byte[8];
        bytes[0]= (byte) SET_SN_REQ;
        bytes[1]=0;
        for (int i = 0; i < 6; i++) {
            bytes[i+2]=0x00;
        }
        dataUtils.addCommand(bleFramework, (byte) SET_SN_REQ, bytes);
    }

    public static void getDeviceCurrentReq(DataUtils dataUtils, BLEFramework bleFramework) {
        dataUtils.addCommand(bleFramework, (byte) GET_DEVICE_CURRENT_REQ, new byte[]{0x00});
    }

    public static void setMagicReq(DataUtils dataUtils, BLEFramework bleFramework, byte magic) {
        byte[] bytes=new byte[3];
        bytes[0]= (byte) SET_MAGIC_REQ;
        bytes[1]=1;
        bytes[2]=magic;
        dataUtils.addCommand(bleFramework, (byte) SET_MAGIC_REQ, bytes);
    }

    public static void readMagicReq(DataUtils dataUtils, BLEFramework bleFramework) {
        byte[] bytes=new byte[3];
        bytes[0]= (byte) SET_MAGIC_REQ;
        bytes[1]=0;
        bytes[2]=0x00;
        dataUtils.addCommand(bleFramework, (byte) SET_MAGIC_REQ, bytes);
    }
}
