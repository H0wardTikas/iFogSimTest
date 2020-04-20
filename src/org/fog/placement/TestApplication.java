package org.fog.placement;

import java.util.*;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.MyFog.MyRandom;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.*;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * @author Z_HAO 2020/4/14
 */
public class TestApplication {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static Map<Integer,FogDevice> deviceById = new HashMap<Integer,FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();
    static List<Integer> idOfEndDevices = new ArrayList<Integer>();
    static Map<Integer, Map<String, Double>> deadlineInfo = new HashMap<Integer, Map<String, Double>>();
    static Map<Integer, Map<String, Integer>> additionalMipsInfo = new HashMap<Integer, Map<String, Integer>>();
    static boolean CLOUD = false;
    static int numOfGateways = 1;
    static int numOfEndDevPerGateway = 4;
    static double sensingInterval = 0.1;

    static List<Application> remainedApplication = new ArrayList<>();

    public static void refresh(double time) {
        for(FogDevice fogDevice: fogDevices) {
            if(fogDevice.isAllocated() && time >= fogDevice.getBusyTime()) {
                fogDevice.setAllocated(false);
                System.out.println(fogDevice.getName() + " is free");
            }
        }
        remainedApplication.removeIf(Application::isPlaced);
    }

    public static void main(String[] args) {
        Log.printLine("Starting TestApplication...");
        try {
            createFogDevices();
            createApplications();
            for(double time = 0;time < 10;time += 0.1) {
                new ProfitPlacement(fogDevices , sensors , actuators , remainedApplication , null , "" , time);
                refresh(time);
            }
        } catch (Exception e) {
            Log.printLine("Unwanted errors happen");
            e.printStackTrace();
        }
    }

    public static void createFogDevices() throws Exception {
        FogDevice ins1 = createFogDevice("ins#1" , 840 , 190 , 0.038 , 0.0085 , 0.0065 , false , 0);
        FogDevice ins2 = createFogDevice("ins#2" , 824 , 167 , 0.0364 , 0.0064 , 0.005 , false , 0);
        FogDevice ins3 = createFogDevice("ins#3" , 820 , 162 , 0.036 , 0.006 , 0.0047 , false , 1);
        FogDevice ins4 = createFogDevice("ins#4" , 845 , 193 , 0.0386 , 0.0088 , 0.0068 , false , 1);
    }

    public static void createApplications() {
        Application app1 = new Application("app#1" , 110 , 27 , 0.9054 , 0.5838 , 0.017);
        remainedApplication.add(app1);
        Application app2 = new Application("app#2" , 80 , 23 , 0.8062 , 0.5115 , 0.019);
        remainedApplication.add(app2);
        Application app3 = new Application("app#3" , 140 , 30 , 0.8915 , 0.6115 , 0.033);
        remainedApplication.add(app3);
        Application app4 = new Application("app#4" , 90 , 26 , 0.9797 , 0.6075 , 0.034);
        remainedApplication.add(app4);
        Application app5 = new Application("app#5" , 100 , 28 , 0.8384 , 0.6719 , 0.036);
        remainedApplication.add(app5);
        Application app6 = new Application("app#6" , 130 , 21 , 0.921 , 0.5448 , 0.0410);
        remainedApplication.add(app6);
    }

    private static FogDevice createFogDevice(String name , int bandWidth , int rate , double costPerSecond , double calCost , double transCost ,
                                             boolean allocated , int cloud) throws Exception {
        List<Pe> peList = new ArrayList<>();
        //Pe: Processing Element 处理元件，单位百万
        //对应MIPS，百万条指令每秒
        peList.add(new Pe(0 , new PeProvisionerOverbooking(100)));

        int hostId = FogUtils.generateEntityId();
        //这个类就是定义了一堆静态变量以供使用
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(hostId , new RamProvisionerSimple(100) , new BwProvisionerOverbooking(bw) , storage , peList , new StreamOperatorScheduler(peList) , new FogLinearPowerModel(100.0 , 100.0));
        List<Host> hostList = new ArrayList<>();//host 主机
        hostList.add(host);

        //以下偏向模拟的特性
        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 8.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN devices by now
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch , os , vmm , host , time_zone , cost , costPerMem , costPerStorage , costPerBw);

        //以下为正式创建Fog device
        FogDevice fogDevice = null;
        try {
            int schedulingInterval = 10;
            fogDevice = new FogDevice(name , characteristics , new AppModuleAllocationPolicy(hostList) , storageList , schedulingInterval , 100.0 , 100.0 , 0 , 100);
            //scheduling interval = 10
            //up link latency = 0
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        assert fogDevice != null;
        fogDevice.setLevel(0);
        fogDevice.setFogDevice(name , bandWidth , rate , costPerSecond , calCost , transCost , allocated , cloud);
        fogDevices.add(fogDevice);
        return fogDevice;
    }
}





