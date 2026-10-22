package com.odyssey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "odyssey.scoring")
public class ScoringProperties {

    private Weights weights = new Weights();
    private Penalties penalties = new Penalties();

    public ScoringProperties() {
    }

    public ScoringProperties(Weights weights, Penalties penalties) {
        this.weights = weights != null ? weights : new Weights();
        this.penalties = penalties != null ? penalties : new Penalties();
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
}
