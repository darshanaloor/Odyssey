package com.odyssey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "odyssey.scoring")
public class ScoringProperties {

    private Weights weights = new Weights();
    private Penalties penalties = new Penalties();
    private TravellerNeeds travellerNeeds = new TravellerNeeds();

    public ScoringProperties() {
    }

    public ScoringProperties(Weights weights, Penalties penalties, TravellerNeeds travellerNeeds) {
        this.weights = weights != null ? weights : new Weights();
        this.penalties = penalties != null ? penalties : new Penalties();
        this.travellerNeeds = travellerNeeds != null ? travellerNeeds : new TravellerNeeds();
    }

    public Weights getWeights() {
        return weights;
    }

    public void setWeights(Weights weights) {
        this.weights = weights;
    }

    public Penalties getPenalties() {
        return penalties;
    }

    public void setPenalties(Penalties penalties) {
        this.penalties = penalties;
    }

    public TravellerNeeds getTravellerNeeds() {
        return travellerNeeds;
    }

    public void setTravellerNeeds(TravellerNeeds travellerNeeds) {
        this.travellerNeeds = travellerNeeds;
    }

    public static class Weights {
        private double cost = 0.25;
        private double time = 0.35;
        private double reliability = 0.25;
        private double transfers = 0.15;
        private double safetyReliability = 1.0;
        private double safetyBuffer = 0.5;

        public Weights() {
        }

        public Weights(double cost, double time, double reliability, double transfers, double safetyReliability, double safetyBuffer) {
            this.cost = cost;
            this.time = time;
            this.reliability = reliability;
            this.transfers = transfers;
            this.safetyReliability = safetyReliability;
            this.safetyBuffer = safetyBuffer;
        }

        public double getCost() {
            return cost;
        }

        public void setCost(double cost) {
            this.cost = cost;
        }

        public double getTime() {
            return time;
        }

        public void setTime(double time) {
            this.time = time;
        }

        public double getReliability() {
            return reliability;
        }

        public void setReliability(double reliability) {
            this.reliability = reliability;
        }

        public double getTransfers() {
            return transfers;
        }

        public void setTransfers(double transfers) {
            this.transfers = transfers;
        }

        public double getSafetyReliability() {
            return safetyReliability;
        }

        public void setSafetyReliability(double safetyReliability) {
            this.safetyReliability = safetyReliability;
        }

        public double getSafetyBuffer() {
            return safetyBuffer;
        }

        public void setSafetyBuffer(double safetyBuffer) {
            this.safetyBuffer = safetyBuffer;
        }
    }

    public static class Penalties {
        private double constraintViolation = 50.0;
        private double overnightTravel = 30.0;

        public Penalties() {
        }

        public Penalties(double constraintViolation, double overnightTravel) {
            this.constraintViolation = constraintViolation;
            this.overnightTravel = overnightTravel;
        }

        public double getConstraintViolation() {
            return constraintViolation;
        }

        public void setConstraintViolation(double constraintViolation) {
            this.constraintViolation = constraintViolation;
        }

        public double getOvernightTravel() {
            return overnightTravel;
        }

        public void setOvernightTravel(double overnightTravel) {
            this.overnightTravel = overnightTravel;
        }
    }

    public static class TravellerNeeds {
        private double directRouteBonus = 5.0;
        private double transferPenalty = 5.0;
        private double shortWaitBonus = 5.0;
        private double extraBufferBonus = 5.0;
        private double daytimeBonus = 5.0;

        public TravellerNeeds() {
        }

        public TravellerNeeds(double directRouteBonus, double transferPenalty, double shortWaitBonus, double extraBufferBonus, double daytimeBonus) {
            this.directRouteBonus = directRouteBonus;
            this.transferPenalty = transferPenalty;
            this.shortWaitBonus = shortWaitBonus;
            this.extraBufferBonus = extraBufferBonus;
            this.daytimeBonus = daytimeBonus;
        }

        public double getDirectRouteBonus() {
            return directRouteBonus;
        }

        public void setDirectRouteBonus(double directRouteBonus) {
            this.directRouteBonus = directRouteBonus;
        }

        public double getTransferPenalty() {
            return transferPenalty;
        }

        public void setTransferPenalty(double transferPenalty) {
            this.transferPenalty = transferPenalty;
        }

        public double getShortWaitBonus() {
            return shortWaitBonus;
        }

        public void setShortWaitBonus(double shortWaitBonus) {
            this.shortWaitBonus = shortWaitBonus;
        }

        public double getExtraBufferBonus() {
            return extraBufferBonus;
        }

        public void setExtraBufferBonus(double extraBufferBonus) {
            this.extraBufferBonus = extraBufferBonus;
        }

        public double getDaytimeBonus() {
            return daytimeBonus;
        }

        public void setDaytimeBonus(double daytimeBonus) {
            this.daytimeBonus = daytimeBonus;
        }
    }
}
