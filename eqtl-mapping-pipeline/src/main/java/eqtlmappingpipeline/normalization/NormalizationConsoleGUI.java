/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eqtlmappingpipeline.normalization;

import java.io.IOException;

import umcg.genetica.io.Gpio;

/**
 * @author harmjan
 */
public class NormalizationConsoleGUI {

	public NormalizationConsoleGUI(String[] args) {

		String in = null;
		String sampleIncludeList = null;
		String probeIncludeList = null;
		String out = null;
		String cov = null;
		String sampleToDatasetFile = null;
		String datasetSpecificCovariates = null;

		boolean fullNorm = true;
		boolean prerank = false;
		boolean runLogTransform = false;
		boolean runMTransform = false;
		boolean runQQNorm = false;
		boolean runCenterScale = false;
		boolean runCovariateAdjustment = false;
		boolean runDatasetCovariateAdjustment = false;
		boolean runPCAdjustment = false;
		boolean orthogonalizecovariates = false;
		boolean useOLSforCovariates = false;
		boolean forceMissingValues = false;
		boolean forceReplacementOfMissingValues = false;
		boolean forceReplacementOfMissingValues2 = false;
		boolean treatZerosAsNulls = false;
		boolean forceNormalDistribution = false;
		boolean storebinary = false;
		int maxPcaToRemove = 100;
		int stepSizePcaRemoval = 5;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			String val = null;

			if (i + 1 < args.length) {
				val = args[i + 1];
			}
			if (arg.equals("--in")) {
				in = val;
			}
			if (arg.equals("--out")) {
				out = val;
			}
			if (arg.equals("--logtransform")) {
				runLogTransform = true;
				fullNorm = false;
			}
			if (arg.equals("--prerank")) {
				prerank = true;
				fullNorm = false;
			}
			if (arg.equals("--binary")) {
				storebinary = true;
			}
			if (arg.equals("--Mtransform")) {
				runMTransform = true;
				fullNorm = false;
			}
			if (arg.equals("--qqnorm")) {
				runQQNorm = true;
				fullNorm = false;
			}
			if (arg.equals("--centerscale")) {
				runCenterScale = true;
				fullNorm = false;
			}
			if (arg.equals("--adjustcovariates")) {
				runCovariateAdjustment = true;
				fullNorm = false;
			}
			if (arg.equals("--adjustdatasetcovariates")) {
				runDatasetCovariateAdjustment = true;
				fullNorm = false;
			}
			if (arg.equals("--adjustPCA")) {
				runPCAdjustment = true;
				fullNorm = false;
			}

			if (arg.equals("--cov")) {
				cov = val;
			}
			if (arg.equals("--datasetcov")) {
				datasetSpecificCovariates = val;
			}
			if (arg.equals("--sampletodataset")) {
				sampleToDatasetFile = val;
			}

			if (arg.equals("--covpca")) {
				orthogonalizecovariates = true;
			}
			if (arg.equals("--covols")) {
				useOLSforCovariates = true;
			}

			if (arg.equals("--maxnrpcaremoved")) {
				maxPcaToRemove = Integer.parseInt(val);
			}
			if (arg.equals("--stepsizepcaremoval")) {
				stepSizePcaRemoval = Integer.parseInt(val);
			}
			if (arg.equals("--forceReplacementOfMissingValuesSampleBased")) {
				forceReplacementOfMissingValues = true;
			}
			if (arg.equals("--forceReplacementOfMissingValuesProbeBased")) {
				forceReplacementOfMissingValues2 = true;
			}
			if (arg.equals("--forceMissingValues")) {
				fullNorm = false;
				forceMissingValues = true;
			}
			if (arg.equals("--treatZerosAsNulls")) {
				fullNorm = false;
				treatZerosAsNulls = true;
			}
			if (arg.equals("--forceNormalDist")) {
				forceNormalDistribution = true;
				fullNorm = false;
			}
			if (arg.equals("--sampleInclude")) {
				sampleIncludeList = val;
				fullNorm = false;
			}
			if (arg.equals("--probeInclude")) {
				probeIncludeList = val;
				fullNorm = false;
			}
		}

		if (in == null) {
			System.out.println("Please supply your data file.\n");
			printUsage();
			System.exit(0);
		}

		if (!Gpio.exists(in)) {
			System.out.println("Error: the file you specified does not exist.\n");
			System.out.println("Could not find file: " + in);
			System.exit(-1);
		}
		if (runLogTransform && runMTransform) {
			throw new IllegalArgumentException("Error: can't perform both log and M-value transformation.");
		}

		if (runPCAdjustment && forceNormalDistribution) {
			throw new IllegalArgumentException("Error: can't perform both PC removal and force normal distribution.");
		}

		if ((forceMissingValues && (forceReplacementOfMissingValues || forceReplacementOfMissingValues2)) || (forceReplacementOfMissingValues && (forceMissingValues || forceReplacementOfMissingValues2)) || (forceReplacementOfMissingValues2 && (forceReplacementOfMissingValues || forceMissingValues))) {
			throw new IllegalArgumentException("Error: can't perform two forces on missing values.");
		}

		if (forceMissingValues && !treatZerosAsNulls) {
			runLogTransform = false;
			runMTransform = false;
			runCenterScale = false;
			runPCAdjustment = false;
			runCovariateAdjustment = false;
		}

		try {
			Normalizer p = new Normalizer();
			if (storebinary) {
				p.saveBinary = true;
			}

			if (prerank) {
				p.rank(in, out);
				System.exit(0);
			}

			if (!fullNorm) {
				p.normalize(in, probeIncludeList, sampleIncludeList, maxPcaToRemove, stepSizePcaRemoval, cov, datasetSpecificCovariates, sampleToDatasetFile,
						orthogonalizecovariates, useOLSforCovariates, out,
						runQQNorm, runLogTransform, runMTransform, runCenterScale, runPCAdjustment,
						runCovariateAdjustment, runDatasetCovariateAdjustment, forceMissingValues, forceReplacementOfMissingValues,
						forceReplacementOfMissingValues2, treatZerosAsNulls, forceNormalDistribution);
			} else {

				// run full normalization
				p.normalize(in, null, null, maxPcaToRemove, stepSizePcaRemoval, cov, datasetSpecificCovariates, sampleToDatasetFile,
						orthogonalizecovariates, useOLSforCovariates, out,true, true, false, true, true, true, true, false, false, false,
						false, false);
			}

			System.out.println("Done.");

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void printUsage() {
		System.out.println("Normalization tool\n"
				+ "Parameters for full normalization (which includes the following procedures in order: qqnorm, log2transform, centerscale, [covariate adjustment], PCA adjustment):\n"
				+ "--in\t\t\tstring\t\tProvide the location of the expression data matrix\n"
				+ "--out\t\t\tdir\t\tProvide the directory to output files [optional; default is --in directory]\n"
				+ "\n"
				+ "Specific normalization methodologies (if multiple methodologies are specified, they will follow the order of full normalization; see above):\n"
				+ "--qqnorm\t\t\t\tQuantile normalization\n"
				+ "--logtransform\t\t\t\tRun log2 transformation\n"
				+ "--Mtransform\t\t\t\tRun M-val (log) transformation for methylation Beta values\n"
				+ "--adjustcovariates\t\t\tRun covariate adjustment\n"
				+ "--adjustdatasetcovariates\t\t\tRun covariate adjustment per dataset\n"
				+ "--centerscale\t\t\t\tCenter the mean to 0, linearly scale using standard deviation\n"
				+ "--adjustPCA\t\t\t\tRun PCA adjustment \n"
				+ "--forceNormalDist\t\t\t\tConvert the data to a normal distribution per gene \n"
				+ "--sampleInclude\t\t\t\tList of high quality sample, other samples will be removed.\n"
				+ "--prerank\t\t\t\tRank the data before normalization\n"
				+ "\n"
				+ "Covariate adjustment parameters:\n"
				+ "--cov\t\t\tstring\t\tCovariates to remove\n"
				+ "--covpca\t\t\t\tOrthogonalize covariates using PCA before regression\n"
				+ "--covols\t\t\t\tUse multivariate OLS based approach in stead of PCA\n"
				+ "--datasetcov\t\t\t\tCovariates to remove per dataset\n"
				+ "--sampletodataset\t\t\t\tMapping file linking sample IDs to datasets (required for --datasetcov)\n"

				+ "\n"
				+ "PCA parameters\n"
				+ "--maxnrpcaremoved\tinteger\t\tMaximum number of PCs to remove\n"
				+ "--stepsizepcaremoval\tinteger\t\tStep size for PC removal\n"
				+ "\n"
				+ "Selection\n"
				+ "--probeInclude\tFile\tList of probes to keep in the file\n"
				+ "--sampleInclude\tFile\tList of samples to keep in the file\n"
				+ "\n"
				+ "Additional QN missing value parameters (Only one of the force option's is allowed at once.)\n"
				+ "--forceMissingValues\tUses a Quantile normalization strategy where missing values are ignored. If chosen, without --treatZerosAsNulls, only QN will be performed.\n"
				+ "--forceReplacementOfMissingValuesSampleBased\tUses a Quantile normalization strategy where missing values are ignored and replaced by sample mean.\n"
				+ "--forceReplacementOfMissingValuesProbeBased\tUses a Quantile normalization strategy where missing values are ignored and replaced by probe mean.\n"
				+ "--treatZerosAsNulls\tTransforms all zeros to nulls during QN.\n" +
				"\n" +
				"--binary\tStore output as binary files");
	}
}
