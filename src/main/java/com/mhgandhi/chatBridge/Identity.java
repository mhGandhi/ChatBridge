package com.mhgandhi.chatBridge;


import java.util.Objects;

public class Identity {
    public record McIdentity(String uuid, String name, String avatarURL){
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof McIdentity that)) return false;
            return Objects.equals(uuid, that.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(uuid);
        }
    };
    public record DcIdentity(String id, String name, String avatarURL){
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DcIdentity that)) return false;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    };
    //todo just use Member and Player instead?

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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Identity identity)) return false;
        return Objects.equals(mcIdentity, identity.mcIdentity) && Objects.equals(dcIdentity, identity.dcIdentity);
    }//todo true if only one matches aswell? (or one matches other is null)

    @Override
    public int hashCode() {
        return Objects.hash(mcIdentity, dcIdentity);
    }
}
