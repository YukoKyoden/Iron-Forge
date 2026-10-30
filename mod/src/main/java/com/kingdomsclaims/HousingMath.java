package com.kingdomsclaims;

import java.util.*;

/** Deterministic bounded geometry estimator, independent of Minecraft rendering and world loading. */
public final class HousingMath {
    private HousingMath() {}
    public static final int MAX_RESIDENTS = 20;
    private static final int WALKABLE=1, STRUCTURAL=2, SUPPORT=4, DOOR=8, BED_FOOT=16, BED_HEAD=32;
    public record Cell(int x,int y,int z,boolean walkable,boolean structural,boolean support,
                       boolean door,boolean bedFoot,boolean bedHead,String facing) {}
    public record Result(int capacity,int usableFloor,int beds,int storeys,String reason) {
        public boolean qualifies(){return capacity>0;}
    }
    public static Result analyze(int w,int h,int d,List<Cell> pieces){
        if(w<1||h<3||d<1||w>20||h>20||d>20||w*h*d>4096)
            return new Result(0,0,0,0,"selection is too small or too large");
        int[][][] cells=new int[w][h][d];
        String[][][] facing=new String[w][h][d];
        for(Cell part:pieces){
            int x=part.x,y=part.y,z=part.z;
            if(x<0||x>=w||y<0||y>=h||z<0||z>=d)continue;
            int flag=0;
            if(part.walkable)flag|=WALKABLE;
            if(part.structural)flag|=STRUCTURAL;
            if(part.support)flag|=SUPPORT;
            if(part.door)flag|=DOOR;
            if(part.bedFoot)flag|=BED_FOOT;
            if(part.bedHead)flag|=BED_HEAD;
            cells[x][y][z]=flag;
            facing[x][y][z]=part.facing;
        }
        boolean[][][] eligible=new boolean[w][h][d];
        for(int y=1;y<h-1;y++)for(int x=0;x<w;x++)for(int z=0;z<d;z++){
            if((cells[x][y][z]&(STRUCTURAL|DOOR|BED_FOOT|BED_HEAD))!=0)continue;
            if((cells[x][y+1][z]&(STRUCTURAL|DOOR|BED_FOOT|BED_HEAD))!=0)continue;
            if((cells[x][y-1][z]&SUPPORT)==0)continue;
            if(!covered(cells,x,y,z,h))continue;
            eligible[x][y][z]=true;
        }
        boolean[][][] seen=new boolean[w][h][d];
        Map<Integer, Integer> floors=new TreeMap<>();
        Map<Integer, Set<Integer>> validatedBeds=new TreeMap<>();
        for(int y=1;y<h-1;y++)for(int x=0;x<w;x++)for(int z=0;z<d;z++){
            if(!eligible[x][y][z]||seen[x][y][z])continue;
            ArrayDeque<int[]> queue=new ArrayDeque<>();
            List<int[]> room=new ArrayList<>();
            queue.add(new int[]{x,z});seen[x][y][z]=true;
            int walls=0,edges=0;
            while(!queue.isEmpty()){
                int[] v=queue.removeFirst();int cx=v[0],cz=v[1];room.add(v);
                for(int dir=0;dir<4;dir++){
                    int nx=cx+(dir==0?1:dir==1?-1:0),nz=cz+(dir==2?1:dir==3?-1:0);
                    if(nx>=0&&nx<w&&nz>=0&&nz<d&&eligible[nx][y][nz]){
                        if(!seen[nx][y][nz]){seen[nx][y][nz]=true;queue.add(new int[]{nx,nz});}
                    }else{
                        if(nx>=0&&nx<w&&nz>=0&&nz<d &&
                           (cells[nx][y][nz]&(BED_FOOT|BED_HEAD))!=0)continue; // furniture isn't an exterior opening
                        edges++;
                        if(nx>=0&&nx<w&&nz>=0&&nz<d && isWall(cells[nx][y][nz],cells[nx][y+1][nz]))walls++;
                    }
                }
            }
            if(room.size()<7||edges==0||walls*100<edges*75)continue;
            Set<Integer> beds=new HashSet<>();
            for(int[] p:room){int cx=p[0],cz=p[1];
                for(int dir=0;dir<4;dir++){
                    int bx=cx+(dir==0?1:dir==1?-1:0),bz=cz+(dir==2?1:dir==3?-1:0);
                    if(bx<0||bx>=w||bz<0||bz>=d)continue;
                    if((cells[bx][y][bz]&BED_FOOT)!=0 && validBed(cells,facing,bx,y,bz,w,d))beds.add(bx*d+bz);
                }
            }
            int area=room.size()+2*beds.size();
            if(area<9)continue;
            floors.merge(y,area,Integer::sum);
            validatedBeds.computeIfAbsent(y,k->new HashSet<>()).addAll(beds);
        }
        // Avoid furniture or stacked half-blocks creating "bonus" floors within two blocks.
        List<Integer> storeys=new ArrayList<>(floors.keySet());
        storeys.sort(Comparator.<Integer>comparingInt(floors::get).reversed());
        Set<Integer> levels=new HashSet<>();
        for(int y:storeys)if(levels.stream().allMatch(existing->Math.abs(y-existing)>=3))levels.add(y);
        int usable=0;Set<String> allBeds=new HashSet<>();
        for(int y:levels){
            usable+=floors.get(y);
            for(int bed:validatedBeds.getOrDefault(y,Set.of()))allBeds.add(y+":"+bed);
        }
        if(usable<9)return new Result(0,0,0,0,"no sufficiently enclosed, roofed living floor");
        int beds=allBeds.size();
        // No guaranteed four-person bonus. Each 12 indoor floor tiles supports one,
        // complete accessible beds may boost the estimate but never past 1 per 6 tiles.
        int densityLimit=Math.max(1,usable/6);
        int baseline=Math.max(1,usable/12);
        int capacity=Math.min(MAX_RESIDENTS,Math.min(densityLimit,Math.max(baseline,beds)));
        return new Result(capacity,usable,beds,levels.size(),"enclosed indoor space");
    }
    private static boolean isWall(int foot,int head){
        return (foot&DOOR)!=0 || ((foot&STRUCTURAL)!=0 && (head&STRUCTURAL)!=0);
    }
    private static boolean covered(int[][][] c,int x,int y,int z,int h){
        for(int above=y+2;above<Math.min(h,y+9);above++)if((c[x][above][z]&STRUCTURAL)!=0)return true;
        return false;
    }
    private static boolean validBed(int[][][] c,String[][][] facing,int x,int y,int z,int w,int d){
        String face=facing[x][y][z];if(face==null)return false;
        int nx=x,nz=z;
        switch(face){case "north"->nz--;case "south"->nz++;case "east"->nx++;case "west"->nx--;default->{return false;}}
        return nx>=0&&nx<w&&nz>=0&&nz<d && (c[nx][y][nz]&BED_HEAD)!=0 && face.equals(facing[nx][y][nz]);
    }
}
