import java.util.*;
import com.kingdomsclaims.StockpileMath;
public class StockpileMathTest {
    static void check(boolean value,String msg){if(!value)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(StockpileMath.accepted(0,0,64,32)==32,"capacity bounds deposit");
        check(StockpileMath.accepted(Integer.MAX_VALUE-2,0,64,Integer.MAX_VALUE)==2,"overflow guarded");
        check(StockpileMath.accepted(100,100,64,50)==0,"capacity shrink preserves stock, blocks new deposits");
        check(StockpileMath.accepted(0,0,-64,1000)==0,"negative deposit rejected");
        Map<String,Integer> stock=new HashMap<>(Map.of("IRON",100,"WHEAT",2));var before=new HashMap<>(stock);
        check(!StockpileMath.pay(stock,Map.of("IRON",50,"WHEAT",3)),"bundle rejects shortage");check(stock.equals(before),"failed bundle leaves every resource unchanged");
        check(!StockpileMath.pay(stock,Map.of("IRON",-50)),"negative payment rejected");check(stock.equals(before),"negative payment cannot mint stock");
        check(StockpileMath.pay(stock,Map.of("IRON",100,"WHEAT",2)),"exact payment");check(stock.get("IRON")==0&&stock.get("WHEAT")==0,"zero after exact payment");
        check(!StockpileMath.pay(stock,Map.of("IRON",100,"WHEAT",2)),"same payment cannot be charged twice without funds");
        for(int ingots=0;ingots<20;ingots++)for(int nuggets=0;nuggets<20;nuggets++)for(int due=0;due<200;due++){
            var payment=StockpileMath.goldPayment(ingots,nuggets,due);int value=9*ingots+nuggets;
            if(value<due)check(payment==null,"insufficient gold rejected");else{check(payment!=null,"funded payment accepted");check(9*(ingots-payment.get("GOLD"))+nuggets-payment.get("GOLD_NUGGETS")+payment.get("CHANGE")==value-due,"gold value conserved");}
        }
        check(StockpileMath.goldPayment(0,0,-1)==null,"negative payroll rejected");
        System.out.println("Stockpile transaction regression tests passed (80,000 gold cases).");
    }
}
