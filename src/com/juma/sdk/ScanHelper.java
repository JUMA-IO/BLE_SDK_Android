package com.juma.sdk;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;

public class ScanHelper {

	private Context context = null;
	private BluetoothAdapter bluetoothAdapter = null;
	private boolean isScanning = false;
	private String name = null;
	private ScanCallback callback = null;

	private static final String JUMA_SERVICE_UUID = "90FE";

	public static final int STATE_START_SCAN = 0;
	public static final int STATE_STOP_SCAN = 1;

	private static final String SDK_VERSION = "02.00.00.01.151203";

	public ScanHelper(Context context, ScanCallback callback) {
		this.context = context;

		this.callback = callback;

		BluetoothManager bluetoothManager =  (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

		bluetoothAdapter = bluetoothManager.getAdapter();
	}

	/**
	 * Start scan
	 * @param name device name
	 * @return result.true/fasle
	 */
	public boolean startScan(String name){
		if(!checkBluetoothState())
			return false;

		this.name = name;

		if(!isScanning){
			if(bluetoothAdapter.startLeScan(leScanCallback)){
				isScanning = true;
				updateScanState(STATE_START_SCAN);
				return true;
			}else{
				return false;
			}
			
		}else {
			updateScanState(STATE_START_SCAN);
			return true;	
		}
	}

	/**
	 * Stop Scan
	 * @return result.true/false
	 */
	public boolean stopScan(){
		if(!checkBluetoothState())
			return false;

		if(isScanning){
			isScanning = false;
			bluetoothAdapter.stopLeScan(leScanCallback);

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
					updateScanState(STATE_STOP_SCAN);
				}
			}).start();
		}else {
			updateScanState(STATE_STOP_SCAN);
		}

		return true;
	}

	/**
	 * Get Scan status
	 * @return Scan status.true/false
	 */
	public boolean isScanning(){
		return isScanning;
	}

	/**
	 * Get bluetooth Status
	 * @return Bluetooth status.true/false
	 */
	public boolean isEnabled(){
		if(bluetoothAdapter == null)
			return false;
		else
			return bluetoothAdapter.isEnabled();
	}

	/**
	 * Open bluetooth
	 * @return ruselt.true/false
	 */
	public boolean enable(){
		if(bluetoothAdapter == null)
			return false;
		else
			return bluetoothAdapter.enable();
	}

	/**
	 * Close bluetooth
	 * @return ruselt.true/false
	 */
	public boolean disable(){
		if(bluetoothAdapter == null)
			return false;
		else
			return bluetoothAdapter.disable();
	}

	/**
	 * Get SDK version.
	 * @return SDK version
	 */
	public static String getVersion(){
		return SDK_VERSION;
	}

	private BluetoothAdapter.LeScanCallback leScanCallback = new LeScanCallback() {

		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			if(!getServiceUuids(scanRecord).contains(JUMA_SERVICE_UUID))
				return;
			
			if(name == null || name.equals("") || name.equals(device.getName())){
				callback.onDiscover(new JumaDevice(context, ScanHelper.this, device.getName(), UUID.fromString(IDEncrypt(device.getAddress(), context))), rssi);
			}
		}
	};

	public interface  ScanCallback{
		public void onDiscover(JumaDevice device, int rssi);
		public void onScanStateChange(int newState);
	}

	private void updateScanState(int status){
		if(callback != null)
			callback.onScanStateChange(status);
	}

	private boolean checkBluetoothState(){
		if(bluetoothAdapter == null || !bluetoothAdapter.isEnabled()){
			return false;
		}else {
			return true;
		}
	}
	
	private List<String> getServiceUuids(byte[] advData) {
		List<String> uuids = new ArrayList<String>();
		boolean isOver = true;
		while (isOver) {
			int dataLen = advData[0];
			if (dataLen == 0) {
				isOver = false;
				break;
			}
			byte[] allData = new byte[dataLen];
			for (int i = 0; i < allData.length; i++) {
				allData[i] = advData[i + 1];
			}
			byte[] type = { allData[0] };
			byte[] data = new byte[allData.length - 1];
			for (int i = 0; i < data.length; i++) {
				data[i] = allData[i + 1];
			}
			if ((0xff & type[0]) == 0x02) {
				byte[] mByte = new byte[data.length];
				for (int i = 0; i < mByte.length; i++) {
					mByte[i] = data[data.length - i - 1];
				}
				uuids.add(toHex(mByte));
			} else if ((0xff & type[0]) == 0x03) {
				int number = data.length / 2;
				for (int i = 0; i < number; i++) {
					byte[] mByte = { data[i * 2], data[i * 2 + 1] };
					uuids.add(toHex(mByte));
				}
			} else if ((0xff & type[0]) == 0x04) {
				byte[] mByte = new byte[data.length];
				for (int i = 0; i < mByte.length; i++) {
					mByte[i] = data[data.length - i - 1];
				}
				uuids.add(toHex(mByte));
			} else if ((0xff & type[0]) == 0x05) {
				int number = data.length / 4;
				for (int i = 0; i < number; i++) {
					byte[] mByte = { data[i * 4], data[i * 4 + 1],
							data[i * 4 + 2], data[i * 4 + 3] };
					uuids.add(toHex(mByte));
				}
			} else if ((0xff & type[0]) == 0x06) {
				byte[] mByte = new byte[data.length];
				for (int i = 0; i < mByte.length; i++) {
					mByte[i] = data[data.length - i - 1];
				}
				uuids.add(toHex(mByte));
			} else if ((0xff & type[0]) == 0x07) {
				int number = data.length / 16;
				for (int i = 0; i < number; i++) {
					byte[] mByte = { data[i * 16], data[i * 16 + 1],
							data[i * 16 + 2], data[i * 16 + 3],
							data[i * 16 + 4], data[i * 16 + 5],
							data[i * 16 + 6], data[i * 16 + 7],
							data[i * 16 + 8], data[i * 16 + 9],
							data[i * 16 + 10], data[i * 16 + 11],
							data[i * 16 + 12], data[i * 16 + 13],
							data[i * 16 + 14], data[i * 16 + 15] };
					uuids.add(toHex(mByte));
				}
			}
			byte[] newData = new byte[advData.length - dataLen - 1];
			for (int i = 0; i < newData.length; i++) {
				newData[i] = advData[i + 1 + dataLen];
			}
			advData = newData;
		}
		return uuids;
	}
	
	private void appendHex(StringBuffer sb, byte b) { 
		String HEX = "0123456789ABCDEF"; 
		sb.append(HEX.charAt((b>>4)&0x0f)).append(HEX.charAt(b&0x0f)); 
	}  

	@SuppressLint("DefaultLocale")
	private String IDEncrypt(String cleartextId , Context context){
		StringBuffer sb = new StringBuffer(cleartextId);
		for (int i = 2; i < sb.length(); i+=2) {
			sb.deleteCharAt(i);
		}
		StringBuffer sb2 = null;
		try {
			sb2 = new StringBuffer(encrypt(getLocalBluetoothMAC(context), sb.toString()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (int i = 0; i < 4; i++) {
			sb2.insert(8 + i * 5, "-");
		}
		return sb2.toString().toLowerCase();
	}

	private String encrypt(String seed, String cleartext) throws Exception { 
		byte[] rawKey = getRawKey(seed.getBytes()); 
		byte[] result = encrypt(rawKey, cleartext.getBytes()); 
		return toHex(result); 
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

	private byte[] encrypt(byte[] raw, byte[] clear) throws Exception { 
		SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES"); 
		Cipher cipher = Cipher.getInstance("AES"); 
		cipher.init(Cipher.ENCRYPT_MODE, skeySpec); 
		byte[] encrypted = cipher.doFinal(clear); 
		return encrypted; 
	} 

	private String toHex(byte[] buf) { 
		if (buf == null) 
			return ""; 
		StringBuffer result = new StringBuffer(2*buf.length); 
		for (int i = 0; i < buf.length; i++) { 
			appendHex(result, buf[i]); 
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
