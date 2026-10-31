import com.kingdomsclaims.RailNetwork;
import java.util.*;
public class RailNetworkTest {
    static final Map<RailNetwork.Point,String> rails=new HashMap<>();static final Set<RailNetwork.Point> blocked=new HashSet<>();
    static RailNetwork.Point p(int x,int y,int z){return new RailNetwork.Point(x,y,z);}
    static RailNetwork.Result scan(RailNetwork.Point entrance){return RailNetwork.walk(entrance,new RailNetwork.Track(){public String shape(RailNetwork.Point p){return rails.get(p);}public boolean clear(RailNetwork.Point p){return !blocked.contains(p);}},512,128,32);}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        for(int x=0;x<10;x++)rails.put(p(x,32,0),"east_west");check(scan(p(0,32,0)).sections()==10,"10 clear sections");
        blocked.add(p(5,32,0));check(scan(p(0,32,0)).sections()==5,"blocked tunnel cuts downstream output");blocked.clear();rails.remove(p(5,32,0));check(scan(p(0,32,0)).sections()==5,"broken rail cuts downstream output");
        rails.clear();rails.put(p(0,33,0),"east_west");rails.put(p(1,32,0),"ascending_west");rails.put(p(2,32,0),"east_west");check(scan(p(0,33,0)).sections()==2,"descending slope connects; surface rail earns nothing");
        rails.put(p(2,32,0),"north_west");rails.put(p(2,32,-1),"north_south");check(scan(p(0,33,0)).sections()==3,"curves connect reciprocally");rails.put(p(2,32,-1),"east_west");check(scan(p(0,33,0)).sections()==2,"adjacent disconnected rails excluded");
        rails.clear();for(int x=0;x<200;x++)rails.put(p(x,0,0),"east_west");check(scan(p(0,0,0)).sections()==129,"radius bounded");
        rails.clear();rails.put(p(0,0,0),"south_east");rails.put(p(1,0,0),"east_west");rails.put(p(2,0,0),"south_west");rails.put(p(2,0,1),"north_south");rails.put(p(2,0,2),"north_west");rails.put(p(1,0,2),"east_west");rails.put(p(0,0,2),"north_east");rails.put(p(0,0,1),"north_south");check(scan(p(0,0,0)).sections()==8,"rail loop counts each section once");
        int carry=0,total=0;for(int i=0;i<100;i++){total+=RailNetwork.wholeIron(3,carry);carry=RailNetwork.carry(3,carry);}check(total==150&&carry==0,"odd half iron never lost across cycles");
        System.out.println("Mine rail connectivity, slopes, clearance stops, depth, bounds and fractional yield regressions passed.");
    }
}
