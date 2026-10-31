import com.kingdomsclaims.ArchitectInputGate;
public final class ArchitectInputGateTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){var gate=new ArchitectInputGate();
        check(!gate.press(0),"inactive tools ignore clicks");check(gate.start(),"first activation opens selection");check(!gate.start(),"held use cannot restart selection");
        check(gate.press(0),"first press selects once");for(int i=0;i<100;i++)check(!gate.press(0),"held/repeated press cannot spam corners");
        gate.release(0);check(gate.press(0),"new physical click selects the next corner");
        check(!gate.start(),"activation cannot reset a partial selection");gate.release(1);check(gate.press(1),"right press recorded");check(!gate.press(1),"right repeats ignored");gate.release(1);
        gate.stop();check(!gate.press(0),"exit disables input");check(gate.start()&&gate.press(0),"fresh session accepts a new click");
        System.out.println("Architect click/hold/release/re-entry regression tests passed.");
    }
}
