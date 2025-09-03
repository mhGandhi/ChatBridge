package com.mhgandhi.dcBridge;


public class Identity {
    public record McIdentity(String uuid, String name, String avatarURL){};
    public record DcIdentity(String id, String name, String avatarURL){};//todo just use Member and Player instead?

    private final McIdentity mcIdentity;
    private final DcIdentity dcIdentity;

    public static final Identity server = new Identity(null,null);

    public Identity(McIdentity pMI, DcIdentity pDI){
        mcIdentity = pMI;
        dcIdentity = pDI;
    }

    public McIdentity getMcIdentity(){
        return this.mcIdentity;
    }
    public DcIdentity getDcIdentity(){
        return this.dcIdentity;
    }
}
