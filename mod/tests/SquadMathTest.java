import com.kingdomsclaims.SquadMath;
public class SquadMathTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){for(int total=1;total<=10000;total++){int left=total,field=0,entities=0;for(int slots=30;slots>0&&left>0;slots--){int n=SquadMath.weight(left,slots);check(n>=1&&n<=10&&n<=left);left-=n;field+=n;entities++;}check(left+field==total&&entities<=30&&field<=300);if(total==90)check(entities==30&&field==90);}System.out.println("Squad compression and troop conservation regressions passed (1-10000 troops).");}
}
