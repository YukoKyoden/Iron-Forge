package com.kingdomsclaims;
import java.util.*;
/** World-independent geometry used by the actual mine survey. */
public final class RailNetwork {
    public record Point(int x,int y,int z){Point offset(int dx,int dy,int dz){return new Point(x+dx,y+dy,z+dz);}}
    public interface Track {String shape(Point p);boolean clear(Point p);}
    public record Result(Set<Point> rails,int sections){}
    private static final int[][] DIR={{0,-1},{1,0},{0,1},{-1,0}};
    private static int[] ends(String s){return switch(s){case "north_south","ascending_north","ascending_south"->new int[]{0,2};case "east_west","ascending_east","ascending_west"->new int[]{1,3};case "south_east"->new int[]{2,1};case "south_west"->new int[]{2,3};case "north_east"->new int[]{0,1};case "north_west"->new int[]{0,3};default->new int[0];};}
    private static int rise(String s,int d){return s.equals("ascending_"+new String[]{"north","east","south","west"}[d])?1:0;}
    public static Result walk(Point entrance,Track track,int max,int radius,int depth){Set<Point> visited=new LinkedHashSet<>();Set<Point> tried=new HashSet<>();ArrayDeque<Point> queue=new ArrayDeque<>();queue.add(entrance);int count=0;
        while(!queue.isEmpty()&&visited.size()<max){Point p=queue.removeFirst();if(!tried.add(p)||Math.abs((long)p.x-entrance.x)>radius||Math.abs((long)p.z-entrance.z)>radius)continue;String s=track.shape(p);if(s==null||!track.clear(p))continue;visited.add(p);if(p.y<=depth)count++;
            for(int d:ends(s))for(int dy=-1;dy<=1;dy++){Point q=p.offset(DIR[d][0],dy,DIR[d][1]);String other=track.shape(q);if(other==null)continue;for(int reverse:ends(other))if(reverse==(d+2)%4&&p.y+rise(s,d)==q.y+rise(other,reverse))queue.add(q);}
        }return new Result(visited,count);
    }
    public static int wholeIron(int sections,int carry){return (Math.max(0,sections)+Math.max(0,carry))/2;}
    public static int carry(int sections,int carry){return (Math.max(0,sections)+Math.max(0,carry))%2;}
}
