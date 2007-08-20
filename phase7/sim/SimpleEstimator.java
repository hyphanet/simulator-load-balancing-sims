package sim;

class SimpleEstimator {

    double[] length_times;         // Map from [0,1]->[0,maxt]
    static double alpha = 0.1;     // Parameter for exponential moving average

    /*
     * @resolution: resolution on estimation in keyspace
     */
    public SimpleEstimator(int resolution) {
	length_times = new double[resolution];
    }

    /*
     * update estimator with an observed roundtrip time for a distance
     *
     * @dist: distance for service
     * @rtt: observed roundtrip time when using this estimator
     */
    public void update(double dist,double rtt) {
	// int i = (int) Math.floor(length_times.length * 2*dist);
	int i = (int) Math.floor(length_times.length*dist);

	if(length_times[i] == 0) {
	    length_times[i] = rtt;
	} else {
	    length_times[i] = rtt*alpha + length_times[i]*(1-alpha);
	}
    }

    /*
     * estimate required time based on previously seen performance,
     * simple linear average when no performance seen
     *
     * @dist: distance we wonder about
     */
    public double estimate(double dist) {
	// int i = (int) Math.floor(length_times.length * 2*dist);
	int i = (int) Math.floor(length_times.length*dist);

	if(i==length_times.length)  // Boundary node, farthest away
	    i--;

	// Direct estimation?
	if (length_times[i] != 0) {
	    return length_times[i];
	} else {

	    if((i==0) || (i==length_times.length-1)) {
		return 0;
	    } else {
		// Try interferring estimation from farther and nearer
		double lower=0,upper=0;		

		for (int j=i-1; j>0; j--) {
		    if (length_times[j]>0) {
			lower = length_times[j];
			break;
		    }
		}

		for (int j=i+1; j<(length_times.length-1); j++) {
		    if (length_times[j]>0) {
			upper = length_times[j];
			break;
		    }
		}

		if ((lower==0)||(upper==0)) {
		    return 0;
		} else {
		    return ((lower+upper)/2);
		}

	    }
	}
	
    }

}
