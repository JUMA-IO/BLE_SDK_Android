package com.juma.sdk;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.util.ByteArrayBuffer;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

public class JumaDevice {

	private BluetoothManager bluetoothManager = null;
	private BluetoothAdapter bluetoothAdapter = null;

	private String name = null;
	private UUID uuid = null;
	private Context context = null;
	private JumaDeviceCallback callback = null;
	private ScanHelper scanHelper = null;
	private JumaDevice device;

	private boolean isConnected = false;
	private boolean isConnecting = false;
	private boolean isDisconnecting = false;

	private BluetoothGatt bluetoothGatt = null;
	private BluetoothGattService bluetoothGattService = null;
	private BluetoothGattCharacteristic bluetoothGattCharacteristicCommand = null;
	private BluetoothGattCharacteristic bluetoothGattCharacteristicEvent = null;
	private BluetoothGattCharacteristic bluetoothGattCharacteristicBulkOut = null;
	private BluetoothGattCharacteristic bluetoothGattCharacteristicBulkIn = null;
	private BluetoothGattDescriptor bluetoothGattDescriptorEvent = null;
	private BluetoothGattDescriptor bluetoothGattDescriptorBulkIn = null;

	private static final UUID SERVICE_UUID = UUID.fromString("00008000-60b2-21f8-bce3-94eea697f98c");
	private static final UUID CHARACTERISTIC_UUID_COMMAND = UUID.fromString("00008001-60b2-21f8-bce3-94eea697f98c");
	private static final UUID CHARACTERISTIC_UUID_EVENT = UUID.fromString("00008002-60b2-21f8-bce3-94eea697f98c");
	private static final UUID CHARACTERISTIC_UUID_BULK_OUT = UUID.fromString("00008003-60b2-21f8-bce3-94eea697f98c");
	private static final UUID CHARACTERISTIC_UUID_BULK_IN = UUID.fromString("00008004-60b2-21f8-bce3-94eea697f98c");
	private static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private static final byte MESSAGE_TYPE_OTA_DATA = (byte)0X81;
	private static final byte MESSAGE_TYPE_OTA_SET = (byte)0x82;

	private static final byte OTA_HEADER_BEGIN = 0X00;
	private static final byte OTA_HEADER_END = 0x01;
	private static final byte OTA_HEADER_DATA = 0X02;

	private static final int MESSAGE_MAX_LENGTH = 198;

	private byte[] readyMessage = null;
	private byte[] sendMessage = null;
	private byte[] firmwareData = null;
	private int index = 0;
	private boolean isUpdating = false;

	private static final String SDK_VERSION = "02.00.00.01.151203";
	
	public static final int SUCCESS = 0;
	public static final int ERROR = 1;
	public static final int STATE_CONNECTED = 0;
	public static final int STATE_DISCONNECTED = 1;
	

	JumaDevice(Context context, ScanHelper scanHelper, String name, UUID uuid) {
		this.name = name;
		this.uuid = uuid;
		this.context = context;
		this.scanHelper = scanHelper;
		this.device = this;
	}
	public JumaDevice() {
		this.name = null;
		this.uuid = null;
		this.context = null;
		this.scanHelper = null;
		this.device = this;
	}
	/**
	 * Get device name
	 * @return device name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get device uuid
	 * @return device uuid
	 */
	public UUID getUuid() {
		return uuid;
	}

	/**
	 * Connect device
	 * @param callback
	 * @return result.true/false
	 */
	public synchronized boolean connect(JumaDeviceCallback callback){
		if(isConnecting)
			return false;

		if(bluetoothManager == null)
			bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

		if(bluetoothAdapter == null)
			bluetoothAdapter = bluetoothManager.getAdapter();

		if (!checkBluetoothState()) {
			return false;
		}

		if(scanHelper.isScanning())
			if(!scanHelper.stopScan())
				return false;

		this.callback = callback;

		if(isConnected && uuid != null){
			if(callback != null)
				callback.onConnectionStateChange(SUCCESS, STATE_CONNECTED,device);
			return true;
		}

		isConnecting = true;	

		try {
			if(bluetoothGatt != null)
				bluetoothGatt.close();
			bluetoothGatt = bluetoothAdapter.getRemoteDevice(IDDecrypt(uuid.toString(), context)).connectGatt(context, false, gattCallback);
		} catch (Exception e) {
			isConnecting = false;
			return false;
		}

		return true;
	}

	/**
	 * Disconnect device
	 * @return result.true/false
	 */
	public boolean disconnect(){
		if(isDisconnecting)
			return false;

		if (!checkBluetoothState()) {
			return false;
		}

		if(isConnected && bluetoothGatt != null){
			isDisconnecting = true;
			bluetoothGatt.disconnect();
			return true;
		}

		return false;
	}

	/**
	 * Get remote rssi
	 * @return result.true/false
	 */
	public boolean getRemoteRssi(){

		if (!checkBluetoothState()) {
			return false;
		}

		if(isConnected && bluetoothGatt != null){
			bluetoothGatt.readRemoteRssi();
			return true;
		}

		return false;
	}

	/**
	 * Send message
	 * @param type message type
	 * @param message message data
	 * @return result.true/false
	 */
	public boolean send(byte type, byte[] message){

		if(Integer.parseInt(toHex(new byte[]{type}), 16) > 128){
			return false;
		}

		return readySend(type, message);
	}

	private boolean readySend(byte type, byte[] message){
		if (!checkBluetoothState()) {
			return false;
		}

		if(readyMessage != null){
			return false;
		}

		if(message.length > MESSAGE_MAX_LENGTH){
			return false;
		}

		if(!isConnected || bluetoothGatt == null){
			return false;
		}

		readyMessage = addMessageHead(type, message);
		sendMessage = message;

		bluetoothGattCharacteristicCommand.setValue(readPacket(readyMessage));

		if(!bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristicCommand)){
			readyMessage = null;
			sendMessage = null;

			return false;
		}

		readyMessage = updateMessage(readyMessage);
		return true;
	}


	/**
	 * Get SDK version.
	 * @return SDK version
	 */
	public static String getVersion(){
		return SDK_VERSION;
	}

	/**
	 * Set OTA MODE
	 * @return result.true/false
	 */
	public boolean setOtaMode(){
		return readySend(MESSAGE_TYPE_OTA_SET, new byte[]{0x4f, 0x54, 0x41, 0x5f, 0x4d, 0x4f, 0x44, 0x45, 0x00});
	}

	/**
	 * Update firmware 
	 * @param url
	 * @return result.true/false
	 */
	public boolean updateFirmware(String url){

		if(isUpdating){
			return false;
		}else{
			isUpdating = true;
		}

		new DownloadThread(url, new DownloadCallback() {

			@Override
			public void onDownload(boolean state, byte[] data) {
				if(!state){
					if(callback != null){
						callback.onUpdateFirmware(ERROR);
					}

					isUpdating = false;

					firmwareData = null;
				}else{
					firmwareData = data;

					index = 0;

					if(!readySend(MESSAGE_TYPE_OTA_DATA, new byte[]{OTA_HEADER_BEGIN})){
						isUpdating = false;
						firmwareData = null;
						if(callback != null){
							callback.onUpdateFirmware(ERROR);
						}
					}
				}
			}
		}).start();
		return true;
	}

	/**
	 * Get the firmware update status
	 * @return	firmware update status
	 */
	public boolean isFirmwareUpdating(){
		return isUpdating;
	}

	/**
	 * Get device connection status
	 * @return device connection status
	 */
	public boolean isConnected(){
		return isConnected;
	}

	private boolean checkBluetoothState(){
		if(bluetoothAdapter == null || !bluetoothAdapter.isEnabled()){
			return false;
		}else {
			return true;
		}
	}

	private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			if(status == BluetoothGatt.GATT_SUCCESS){

				if(newState == BluetoothGatt.STATE_CONNECTED){

					if(!gatt.discoverServices()){
						gatt.disconnect();
					}
				}else if(newState == BluetoothGatt.STATE_DISCONNECTED){

					gatt.close();

					if(isConnecting){
						isConnecting = false;
						if(callback != null)
							callback.onConnectionStateChange(ERROR, STATE_CONNECTED,device);
						return;
					}

					if (isDisconnecting)
						isDisconnecting = false;

					if(isConnected)
						isConnected = false;

					new Thread(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub

							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

							if(callback != null)
								callback.onConnectionStateChange(SUCCESS, STATE_DISCONNECTED,device);
						}
					}).start();

				}
			}else {
				if(isConnected)
					isConnected = false;

				if(callback != null)
					if(isConnecting){
						isConnecting = false;
						callback.onConnectionStateChange(ERROR, STATE_CONNECTED,device);
					}

				if(isDisconnecting){
					isDisconnecting = false;
					callback.onConnectionStateChange(ERROR, STATE_DISCONNECTED,device);
				}
				
				gatt.close();

				clear();

			}
		};

		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if(status == BluetoothGatt.GATT_SUCCESS){
				bluetoothGattService = gatt.getService(SERVICE_UUID);

				if(bluetoothGattService != null){
					bluetoothGattCharacteristicCommand = bluetoothGattService.getCharacteristic(CHARACTERISTIC_UUID_COMMAND);
					bluetoothGattCharacteristicEvent = bluetoothGattService.getCharacteristic(CHARACTERISTIC_UUID_EVENT);
					bluetoothGattCharacteristicBulkOut = bluetoothGattService.getCharacteristic(CHARACTERISTIC_UUID_BULK_OUT);
					bluetoothGattCharacteristicBulkIn = bluetoothGattService.getCharacteristic(CHARACTERISTIC_UUID_BULK_IN);
				}

				if(bluetoothGattCharacteristicEvent != null)
					bluetoothGattDescriptorEvent = bluetoothGattCharacteristicEvent.getDescriptor(DESCRIPTOR_UUID);

				if(bluetoothGattCharacteristicBulkIn != null)
					bluetoothGattDescriptorBulkIn = bluetoothGattCharacteristicBulkIn.getDescriptor(DESCRIPTOR_UUID);

				if(bluetoothGattService != null 
						&& bluetoothGattCharacteristicCommand != null && bluetoothGattCharacteristicEvent!= null 
						&& bluetoothGattCharacteristicBulkOut != null && bluetoothGattCharacteristicBulkIn != null
						&& bluetoothGattDescriptorEvent != null && bluetoothGattDescriptorBulkIn != null){

					bluetoothGattDescriptorEvent.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

					gatt.setCharacteristicNotification(bluetoothGattCharacteristicEvent, true);

					if(!gatt.writeDescriptor(bluetoothGattDescriptorEvent)){
						gatt.disconnect();
					}
				}else{
					Log.e(JumaDevice.class.getName(), "Does not support the device = "+uuid.toString());
					gatt.disconnect();
				}
			}else if(status == 133){
				isConnecting = false;

				if(callback != null)
					callback.onConnectionStateChange(ERROR, STATE_CONNECTED,device);

				gatt.close();

				clear();

			}else{
				gatt.disconnect();
			}
		};

		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if(status == BluetoothGatt.GATT_SUCCESS){
				if(descriptor.getCharacteristic().getUuid().equals(CHARACTERISTIC_UUID_EVENT)){
					bluetoothGattDescriptorBulkIn.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

					gatt.setCharacteristicNotification(bluetoothGattCharacteristicBulkIn, true);

					if(!gatt.writeDescriptor(bluetoothGattDescriptorBulkIn)){
						gatt.disconnect();
					}

				}else if(descriptor.getCharacteristic().getUuid().equals(CHARACTERISTIC_UUID_BULK_IN)){
					isConnecting = false;
					isConnected = true;
					bluetoothGatt = gatt;
					if(callback != null)
						callback.onConnectionStateChange(SUCCESS, STATE_CONNECTED,device);
				}

			}else if(status == 133){
				isConnecting = false;

				if(callback != null)
					callback.onConnectionStateChange(ERROR, STATE_CONNECTED,device);

				gatt.close();

				clear();
			}else{
				gatt.disconnect();
			}
		};

		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if(status == BluetoothGatt.GATT_SUCCESS){
				if(characteristic.getUuid().equals(CHARACTERISTIC_UUID_COMMAND)){
					if(readyMessage != null && readyMessage.length > 0){
						while (readyMessage != null) {

							bluetoothGattCharacteristicBulkOut.setValue(readPacket(readyMessage));

							if(!bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristicBulkOut)){
								if(callback != null)
									callback.onSend(ERROR,null);
								return;
							}

							readyMessage = updateMessage(readyMessage);

						}
					}

					if(readyMessage == null && !isUpdating)
						if(callback != null)
							callback.onSend(SUCCESS,sendMessage);
				}
			}else {
				if(!isUpdating){
					if(callback != null)
						callback.onSend(ERROR,null);
				}else{
					isUpdating = false;
					if(callback != null){
						callback.onUpdateFirmware(ERROR);
					}
				}
			}
		};

		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			if(characteristic.getUuid().equals(CHARACTERISTIC_UUID_EVENT)){

				byte  msgType = characteristic.getValue()[0];

				if(msgType == MESSAGE_TYPE_OTA_DATA && isUpdating){

					byte header = characteristic.getValue()[2];

					switch (header) {
					case OTA_HEADER_BEGIN:
						if(firmwareData != null){

							if(!readySend(MESSAGE_TYPE_OTA_DATA, readFirmwarePacket(firmwareData))){
								isUpdating = false;
								firmwareData = null;
								if(callback != null){
									callback.onUpdateFirmware(ERROR);
								}
								return;
							}

							index++;

							firmwareData = updateFirmwareData(firmwareData);

						}else{

							isUpdating = false;

							firmwareData = null;

							if(callback != null){
								callback.onUpdateFirmware(ERROR);
							}
						}

						break;
					case OTA_HEADER_DATA:
						if(firmwareData == null){
							if(readySend(MESSAGE_TYPE_OTA_DATA, new byte[]{OTA_HEADER_END})){
								isUpdating = false;
								if(callback != null)
									callback.onUpdateFirmware(SUCCESS);
							}else {
								isUpdating = false;
								firmwareData = null;
								if(callback != null){
									callback.onUpdateFirmware(ERROR);
								}
							}
						}else {

							if(!readySend(MESSAGE_TYPE_OTA_DATA, readFirmwarePacket(firmwareData))){
								isUpdating = false;
								firmwareData = null;
								if(callback != null){
									callback.onUpdateFirmware(ERROR);
								}
							}

							index++;

							firmwareData = updateFirmwareData(firmwareData);
						}

						break;
					}

					return;

				}
			}

			byte[] message = characteristic.getValue();
			byte type = message[0];
			byte[] pureMessage = new byte[message.length - 2];

			for (int i = 0; i < pureMessage.length; i++) {
				pureMessage[i] = message[i + 2];
			}

			if(Integer.parseInt(toHex(new byte[]{type})) < 128)
				if(callback != null)
					callback.onReceive(type, pureMessage);

		};

		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {

			if(status == BluetoothGatt.GATT_SUCCESS){
				if(callback != null)
					callback.onRemoteRssi(SUCCESS, rssi);
			}else if(status == 133){

				if(callback != null)
					callback.onRemoteRssi(ERROR, rssi);

				callback.onConnectionStateChange(ERROR, STATE_CONNECTED,device);

				gatt.close();

				clear();

			}

		};
	};

	private void clear(){
		bluetoothGatt = null;
		bluetoothGattService = null;
		bluetoothGattCharacteristicCommand = null;
		bluetoothGattCharacteristicEvent = null;
		bluetoothGattCharacteristicBulkIn = null;
		bluetoothGattCharacteristicBulkOut = null;
		bluetoothGattDescriptorEvent = null;
		bluetoothGattDescriptorBulkIn = null;
	} 

	private byte[] addMessageHead(byte type, byte[] message){

		if(message == null)
			message = new byte[0];

		ByteArrayBuffer buffer = new ByteArrayBuffer(200);

		buffer.append(type);
		buffer.append(message.length);

		for (int i = 0; i < message.length; i++) {
			buffer.append(message[i]);
		}

		return buffer.toByteArray();
	}

	private byte[] readPacket(byte[] readyMessage){

		byte[] packet  = null;

		if(readyMessage.length <= 20)
			packet = new byte[readyMessage.length];
		else
			packet = new byte[20];

		for (int i = 0; i < packet.length; i++) {
			packet[i] = readyMessage[i];
		}

		return packet;
	}

	private byte[] updateMessage(byte[] readyMessage){

		if(readyMessage.length <= 20){
			return null;
		}else{
			ByteArrayBuffer buffer = new ByteArrayBuffer(200);

			for (int i = 20; i < readyMessage.length; i++) {
				buffer.append(readyMessage[i]);
			}

			return buffer.toByteArray();
		}
	}

	private interface DownloadCallback{
		public void onDownload(boolean state, byte[] data);
	}

	private class DownloadThread extends Thread{

		private DownloadCallback downloadCallback = null;
		private String url = null;

		public DownloadThread(String url, DownloadCallback callback) {
			this.url = url;
			this.downloadCallback =callback;
		}

		@Override
		public void run() {

			try {
				URLConnection connection = new URL(url).openConnection();

				byte[] buffer = new byte[connection.getContentLength()];

				InputStream is = connection.getInputStream();

				is.read(buffer);

				is.close();

				downloadCallback.onDownload(true, buffer);
			}catch (Exception e) {
				downloadCallback.onDownload(false, null);
			}
		}
	}

	private byte[] updateFirmwareData(byte[] firmwateData){

		if(firmwateData.length <= 196){
			return null;
		}else{
			ByteArrayBuffer buffer = new ByteArrayBuffer(firmwateData.length - 196);

			for (int i = 196; i < firmwateData.length; i++) {
				buffer.append(firmwateData[i]);
			}

			return buffer.toByteArray();
		}
	}

	private byte[] readFirmwarePacket(byte[] firmwateData){

		byte[] buffer = null;

		if(firmwateData == null)
			return buffer;

		if(firmwateData.length <= 196)
			buffer = new byte[firmwateData.length + 2];
		else
			buffer = new byte[MESSAGE_MAX_LENGTH];

		buffer[0] = OTA_HEADER_DATA;
		buffer[1] = (byte)index;

		for (int i = 0; i < buffer.length - 2; i++) {
			buffer[i+2] = firmwateData[i];
		}

		return buffer;
	}

	@SuppressLint("DefaultLocale")
	private String IDDecrypt(String encryptedId , Context context) throws Exception{
		StringBuffer sb = new StringBuffer(encryptedId.toUpperCase());
		for (int i = 0; i < 4; i++) {			sb.deleteCharAt(8 + i * 4);
		}
		StringBuffer sb2 = null;
		sb2  = new StringBuffer(decrypt(getLocalBluetoothMAC(context), sb.toString()));
		for (int i = 0; i < 5; i++) {
			sb2.insert(2 + i * 3, ":");
		}
		return sb2.toString();
	}

	private String decrypt(String seed, String encrypted) throws Exception { 
		byte[] rawKey = getRawKey(seed.getBytes()); 
		byte[] enc = toByte(encrypted); 
		byte[] result = decrypt(rawKey, enc); 
		return new String(result); 
	} 

	private byte[] getRawKey(byte[] seed) throws Exception { 
		KeyGenerator kgen = KeyGenerator.getInstance("AES"); 
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG", "Crypto"); 
		sr.setSeed(seed); 
		kgen.init(128, sr); 
		SecretKey skey = kgen.generateKey(); 
		byte[] raw = skey.getEncoded(); 
		return raw; 
	} 

	private byte[] decrypt(byte[] raw, byte[] encrypted) throws Exception { 
		SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES"); 
		Cipher cipher = Cipher.getInstance("AES"); 
		cipher.init(Cipher.DECRYPT_MODE, skeySpec); 
		byte[] decrypted = cipher.doFinal(encrypted); 
		return decrypted; 
	} 

	private byte[] toByte(String hexString) { 
		int len = hexString.length()/2; 
		byte[] result = new byte[len]; 
		for (int i = 0; i < len; i++) 
			result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue(); 
		return result; 
	} 

	private static String toHex(byte[] buf) { 
		if (buf == null) 
			return ""; 
		StringBuffer result = new StringBuffer(2*buf.length); 
		String HEX = "0123456789ABCDEF"; 
		for (int i = 0; i < buf.length; i++) { 
			result.append(HEX.charAt((buf[i]>>4)&0x0f)).append(HEX.charAt(buf[i]&0x0f));
		} 
		return result.toString(); 
	} 

	private String getLocalBluetoothMAC(Context context){
		BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter adapter = manager.getAdapter();
		String mac = adapter.getAddress();
		return mac;
	}

}
