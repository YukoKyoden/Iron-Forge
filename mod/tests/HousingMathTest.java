import java.util.*;
import com.kingdomsclaims.HousingMath;
public class HousingMathTest {
  static List<HousingMath.Cell> house(int size,int levels,boolean walls,boolean roof){
    List<HousingMath.Cell> cells=new ArrayList<>();int h=levels*3+1;
    for(int level=0;level<levels;level++){
      int bot=level*3,top=bot+3;
      for(int x=0;x<size;x++)for(int z=0;z<size;z++){
        cells.add(new HousingMath.Cell(x,bot,z,false,true,true,false,false,false,null));
        if(roof)cells.add(new HousingMath.Cell(x,top,z,false,true,true,false,false,false,null));
        if(walls&&(x==0||x==size-1||z==0||z==size-1)){
          for(int y=bot+1;y<=bot+2;y++)cells.add(new HousingMath.Cell(x,y,z,false,true,true,false,false,false,null));
        }
      }
    }
    return cells;
  }
  static void test(String name,int expected,int w,int h,int d,List<HousingMath.Cell> pieces){
    var r=HousingMath.analyze(w,h,d,pieces);
    System.out.println(name+" "+r);
    if(r.capacity()!=expected)throw new AssertionError(name+" expected "+expected+" actual "+r.capacity());
  }
  public static void main(String[] args){
    test("cottage",1,5,4,5,house(5,1,true,true));
    test("medium",2,7,4,7,house(7,1,true,true));
    test("open pavilion",0,7,4,7,house(7,1,false,true));
    test("roofless",0,7,4,7,house(7,1,true,false));
    test("two storeys",4,7,7,7,house(7,2,true,true));
    test("solid cube",0,5,4,5,new ArrayList<>(house(5,1,true,true)) {{for(int y=1;y<3;y++)for(int x=1;x<4;x++)for(int z=1;z<4;z++)add(new HousingMath.Cell(x,y,z,false,true,true,false,false,false,null));}});
    var beds=house(7,1,true,true);
    beds.add(new HousingMath.Cell(2,1,2,false,false,false,false,true,false,"east"));
    beds.add(new HousingMath.Cell(3,1,2,false,false,false,false,false,true,"east"));
    beds.add(new HousingMath.Cell(2,1,3,false,false,false,false,true,false,"east"));
    beds.add(new HousingMath.Cell(3,1,3,false,false,false,false,false,true,"east"));
    test("two valid beds",2,7,4,7,beds);
    var orphan=house(7,1,true,true);
    orphan.add(new HousingMath.Cell(2,1,2,false,false,false,false,true,false,"east"));
    test("orphan bed no bonus",2,7,4,7,orphan);
    System.out.println("All housing tests passed");
  }
}
