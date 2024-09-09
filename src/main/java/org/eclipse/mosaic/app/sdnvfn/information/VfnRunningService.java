package org.eclipse.mosaic.app.sdnvfn.information;


public class VfnRunningService {

    private String serviceId;
    private final String vehicleId;
    private int replicationId;
    private String jobDataToProcess;
    private long lastResult;


    public VfnRunningService(String serviceId, int replicationId, String vehicleId) {
        this.lastResult = 0;
        this.serviceId = serviceId;
        this.replicationId = replicationId;
        this.vehicleId = vehicleId;
    }

    public long runServiceJob(String serviceMsg){
        this.jobDataToProcess = serviceMsg;
        String[] splitedServiceData = jobDataToProcess.split("&");
        String[] termValue1 = splitedServiceData[0].split(":");
        Integer term1 = Integer.valueOf(termValue1[1]);
        String[] termValue2 = splitedServiceData[1].split(":");
        Integer term2 = Integer.valueOf(termValue2[1]);
        this.lastResult= term1+term2;

        return lastResult;
    }




    public String toString() {
        return "VfnRunningService{" +
                "serviceId=" + this.serviceId +
                ", replicationId=" + this.replicationId +
                ", jobDataToProcess=" + this.jobDataToProcess +
                ", lastResult=" + this.lastResult +
                '}';
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public int getReplicationId() {
        return replicationId;
    }

    public void setReplicationId(int replicationId) {
        this.replicationId = replicationId;
    }

    public String getJobDataToProcess() {
        return jobDataToProcess;
    }

    public void setJobDataToProcess(String jobDataToProcess) {
        this.jobDataToProcess = jobDataToProcess;
    }

    public long getLastResult() {
        return lastResult;
    }

    public void setLastResult(long lastResult) {
        this.lastResult = lastResult;
    }
}
