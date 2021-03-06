package com.hijacker;

/*
    Copyright (C) 2016  Christos Kyriakopoylos

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

import android.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

import static com.hijacker.IsolatedFragment.is_ap;
import static com.hijacker.MainActivity.PROCESS_AIREPLAY;
import static com.hijacker.MainActivity.PROCESS_AIRODUMP;
import static com.hijacker.MainActivity.SORT_BEACONS_FRAMES;
import static com.hijacker.MainActivity.SORT_DATA_FRAMES;
import static com.hijacker.MainActivity.SORT_NOSORT;
import static com.hijacker.MainActivity.SORT_PWR;
import static com.hijacker.MainActivity.completed;
import static com.hijacker.MainActivity.getManuf;
import static com.hijacker.MainActivity.runInHandler;
import static com.hijacker.MainActivity.sort;
import static com.hijacker.MainActivity.startAireplay;
import static com.hijacker.MainActivity.startAirodump;
import static com.hijacker.MainActivity.stop;
import static com.hijacker.MainActivity.toSort;

class ST {
    static List <ST>STs = new ArrayList<>();
    static String paired, not_connected;
    static int connected=0;
    int pwr, id;
    private int frames, lost, total_frames=0, total_lost=0;
    long lastseen = 0;
    boolean added_as_client = false;
    Tile tile;
    String mac, bssid, manuf;
    ST(String mac, String bssid, int pwr, int lost, int frames){
        this.mac = mac;
        this.id = STs.size();
        this.manuf = getManuf(this.mac);
        this.update(bssid, pwr, lost, frames);
        STs.add(this);
        if(sort!=SORT_NOSORT) toSort = true;
    }
    void disconnect(){
        if(is_ap==null){
            //need to switch channel only if there is no isolated ap
            stop(PROCESS_AIRODUMP);
            stop(PROCESS_AIREPLAY);
            startAirodump("--channel " + AP.getAPByMac(this.bssid).ch);
        }
        startAireplay(this.bssid, this.mac);
    }
    void update(String bssid, int pwr, int lost, int frames){
        if(bssid=="na") bssid=null;

        if(added_as_client && bssid==null){
            connected--;
            added_as_client = false;
            AP.getAPByMac(this.bssid).removeClient(this);
        }else if(!added_as_client && bssid!=null){
            AP temp = AP.getAPByMac(bssid);
            if (temp != null){
                this.bssid = bssid;
                temp.addClient(this);
                added_as_client = true;
                connected++;
            }
        }
        if(frames!=this.frames || lost!=this.lost || this.lastseen==0){
            this.lastseen = System.currentTimeMillis();
        }
        if(!toSort && sort!=SORT_NOSORT){
            switch(sort){
                case SORT_BEACONS_FRAMES:
                    toSort = this.frames!=frames;
                    break;
                case SORT_DATA_FRAMES:
                    toSort = this.frames!=frames;
                    break;
                case SORT_PWR:
                    toSort = this.pwr!=pwr;
                    break;
            }
        }

        this.bssid = bssid;
        this.pwr = pwr;
        this.lost = lost;
        this.frames = frames;

        final String b, c;
        if (bssid != null){
            if(AP.getAPByMac(bssid) != null) b = paired + bssid + " (" + AP.getAPByMac(bssid).essid + ")";
            else b = paired + bssid;
        } else b = not_connected;
        c = "PWR: " + this.pwr + " | Frames: " + this.getFrames();
        runInHandler(new Runnable(){
            @Override
            public void run(){
                if(tile!=null) tile.update(ST.this.mac, b, c, ST.this.manuf);
                else tile = new Tile(AP.APs.size() + id, ST.this.mac, b, c, ST.this.manuf, false, null, ST.this);
                if(tile.st==null) tile = null;
                completed = true;
            }
        });
    }
    void showInfo(FragmentManager fragmentManager){
        STDialog dialog = new STDialog();
        dialog.info_st = this;
        dialog.show(fragmentManager, "STDialog");
    }
    public String toString(){
        return mac + '\t' + (bssid==null ? "(not associated)" : bssid) + '\t' + pwr + '\t' + getFrames() + '\t' + getLost() + '\t' + manuf + '\n';
    }
    public int getFrames(){ return total_frames + frames; }
    public int getLost(){ return total_lost + lost; }
    public static void saveData(){
        ST temp;
        for(int i=0;i<ST.STs.size();i++){
            temp = ST.STs.get(i);
            temp.total_frames += temp.frames;
            temp.total_lost += temp.lost;
            temp.frames = 0;
            temp.lost = 0;
        }
    }
    static ST getSTByMac(String mac){
        for(int i=STs.size()-1;i>=0;i--){
            if(mac.equals(STs.get(i).mac)){
                return STs.get(i);
            }
        }
        return null;
    }
    static void clear(){
        STs.clear();
        connected = 0;
    }
}
