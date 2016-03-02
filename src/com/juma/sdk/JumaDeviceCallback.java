package com.juma.sdk;


public abstract class JumaDeviceCallback {
	public void onConnectionStateChange(int status, int newState,JumaDevice device){};
	public void onReceive(byte type, byte[] message){};
	public void onSend(int status,byte[] message){};
	public void onRemoteRssi(int status, int rssi){};
	public void onUpdateFirmware(int status){};
}
