package com.kingdomsclaims;
/** Raw mouse edges for architect tools. Repeated activation/press events never restart a selection. */
public final class ArchitectInputGate {
    private boolean active,left,right;
    public boolean start(){if(active)return false;active=true;left=false;right=false;return true;}
    public boolean press(int button){if(!active)return false;if(button==0){if(left)return false;left=true;return true;}if(button==1){if(right)return false;right=true;return true;}return false;}
    public void release(int button){if(button==0)left=false;if(button==1)right=false;}
    public void stop(){active=false;left=false;right=false;}
}
