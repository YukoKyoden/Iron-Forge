import com.kingdomsclaims.HouseBuildCost;
public final class HouseBuildCostTest {
    static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
    public static void main(String[] args){
        var cottage=HouseBuildCost.forBlocks(218);check(cottage.wood()==55&&cottage.iron()==7&&cottage.gold()==1,"218-block cottage cost");
        var empty=HouseBuildCost.forBlocks(0);check(empty.wood()==0&&empty.iron()==0&&empty.gold()==0,"empty selection has no charge");
        int wood=0,iron=0,gold=0;for(int n=1;n<=4096;n++){var c=HouseBuildCost.forBlocks(n);check(c.wood()>=wood&&c.iron()>=iron&&c.gold()>=gold,"larger house never cheaper");check(c.wood()>=c.iron()&&c.iron()>=c.gold(),"wood dominant, gold small");wood=c.wood();iron=c.iron();gold=c.gold();}
        check(HouseBuildCost.forBlocks(Integer.MAX_VALUE).wood()==1024,"bounded pricing math");System.out.println("House wood/iron/gold pricing regressions passed.");
    }
}
