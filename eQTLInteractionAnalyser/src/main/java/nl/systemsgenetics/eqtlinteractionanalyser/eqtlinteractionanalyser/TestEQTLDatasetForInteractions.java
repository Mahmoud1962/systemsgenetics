/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.systemsgenetics.eqtlinteractionanalyser.eqtlinteractionanalyser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.ranking.NaturalRanking;
import umcg.genetica.genomicboundaries.GenomicBoundary;
import umcg.genetica.io.Gpio;
import umcg.genetica.io.text.TextFile;

/**
 *
 * @author lude
 */
public class TestEQTLDatasetForInteractions {

	String inputDir = null;
	String outputDir = null;
	HashMap<String, GenomicBoundary<Integer>> geneDistanceMap = null;
	String[] primaryCovsToCorrect;

	public TestEQTLDatasetForInteractions(String inputDir, String outputDir) throws IOException {

		this.inputDir = inputDir;
		this.outputDir = outputDir;
		primaryCovsToCorrect = new String[]{"gender", "GC", "MEDIAN_5PRIME_BIAS", "MEDIAN_3PRIME_BIAS", "LLdeep", "RS", "CODAM", "LLS"};
		//preprocessData();
	}

	public TestEQTLDatasetForInteractions(String inputDir, String outputDir, String eQTLfileName, int maxNumTopCovs, String annotationFile, String[] covariatesToCorrect, String[] covariatesToCorrect2, File snpsToSwapFile) throws IOException, Exception {

		System.out.println("Input dir: " + inputDir);
		System.out.println("Output dir: " + outputDir);
		System.out.println("eQTL file: " + eQTLfileName);
		System.out.println("Maximum number of covariates to regress out: " + maxNumTopCovs);
		System.out.println("Covariates to correct for with interaction: " + covariatesToCorrect.toString());
		System.out.println("Covariates to correct for without interaction: " + covariatesToCorrect2.toString());

		this.inputDir = inputDir;
		this.outputDir = outputDir;
		if (!Gpio.exists(outputDir)) {
			Gpio.createDir(outputDir);
		}

		HashMap<String, String> eqtlGenes = getEqtls(eQTLfileName);

		if (annotationFile != null) {
			createGeneDistanceMap(annotationFile);
		}

		//preprocessData();

		TextFile outputTopCovs = new TextFile(outputDir + "/outputTopCovariates.txt", true);

		System.out.print("\nPrimary covariates to correct for before running interaction analysis: ");
		for (String cov : primaryCovsToCorrect) {
			System.out.print("\n\t" + cov);
		}
		System.out.println();


		String[] covsToCorrect = primaryCovsToCorrect;
		int cnt = 0;
		while (cnt < maxNumTopCovs) {
			String topCov = performInteractionAnalysis(covsToCorrect, covariatesToCorrect2, eqtlGenes, outputTopCovs, snpsToSwapFile);
			String[] covsToCorrectNew = new String[covsToCorrect.length + 1];
			for (int c = 0; c < covsToCorrect.length; c++) {
				covsToCorrectNew[c] = covsToCorrect[c];
			}
			covsToCorrectNew[covsToCorrect.length] = topCov;
			covsToCorrect = covsToCorrectNew;
			cnt++;
		}
		outputTopCovs.close();
	}

	/**
	 * Extracts eQTL gene names
	 *
	 * @param fname - eQTL file (in the eqtlmappingpipeline format)
	 * @return gene names in keys of a HashMap
	 * @throws IOException
	 */
	public static HashMap<String, String> getEqtls(String fname) throws IOException {
		if (fname == null) {
			return null;
		}
		TextFile file = new TextFile(fname, false);
		ArrayList<String> genes = file.readAsArrayList(4, TextFile.tab);
		HashMap<String, String> eqtlGenes = new HashMap<String, String>();
		for (String gene : genes) {
			eqtlGenes.put(gene, null);
		}
		file.close();
		return eqtlGenes;

	}

	public void interpretInteractionZScoreMatrix(int maxNumRegressedCovariates) {

		System.out.println("Interpreting the z-score matrix");

		int numPrimaryCovsToCorrect = primaryCovsToCorrect.length;
		for (int nrCovsRemoved = numPrimaryCovsToCorrect; nrCovsRemoved < numPrimaryCovsToCorrect + maxNumRegressedCovariates; nrCovsRemoved++) {
			ExpressionDataset dataset = new ExpressionDataset(outputDir + "/InteractionZScoresMatrix-" + nrCovsRemoved + "Covariates.txt");
			dataset.save(dataset.fileName + ".binary");
		}


		for (int nrCovsRemoved = numPrimaryCovsToCorrect; nrCovsRemoved < numPrimaryCovsToCorrect + maxNumRegressedCovariates; nrCovsRemoved++) {

			ExpressionDataset dataset = new ExpressionDataset(outputDir + "/InteractionZScoresMatrix-" + nrCovsRemoved + "Covariates.txt.binary");
			ExpressionDataset dataset2 = new ExpressionDataset(outputDir + "/InteractionZScoresMatrix-" + (nrCovsRemoved + 1) + "Covariates.txt.binary");

			for (int q = 0; q < dataset.nrSamples; q++) {
				double maxAbsZDiff = 0;
				String output = "";
				for (int p = 0; p < dataset.nrProbes; p++) {
					double zDiff = dataset.rawData[p][q] - dataset2.rawData[p][q];
					double absZDiff = Math.abs(zDiff);
					if (absZDiff > 2 && absZDiff > maxAbsZDiff) {
						maxAbsZDiff = absZDiff;
						output = nrCovsRemoved + "\t" + p + "\t" + dataset.probeNames[p] + "\t" + q + "\t" + dataset.sampleNames[q] + "\t" + dataset.rawData[p][q] + "\t" + dataset2.rawData[p][q] + "\t" + zDiff;
					}
				}
				if (maxAbsZDiff > 2) {
					System.out.println(output);
				}
			}
		}

		System.exit(0);
	}

	public void findChi2SumDifferences(int maxNumRegressedCovariates) {

		int numPrimaryCovsToCorrect = primaryCovsToCorrect.length;
		System.out.println("Interpreting the z-score matrix");
		System.out.println("Preparing the data");
		for (int nrCovsRemoved = numPrimaryCovsToCorrect; nrCovsRemoved < numPrimaryCovsToCorrect + maxNumRegressedCovariates; nrCovsRemoved++) {
			ExpressionDataset dataset = new ExpressionDataset(outputDir + "/InteractionZScoresMatrix-" + nrCovsRemoved + "Covariates.txt");
			dataset.save(dataset.fileName + ".binary");
		}

		System.out.println("Comparing chi2sums");
		for (int nrCovsRemoved = numPrimaryCovsToCorrect; nrCovsRemoved < numPrimaryCovsToCorrect + maxNumRegressedCovariates; nrCovsRemoved++) {

			ExpressionDataset dataset = new ExpressionDataset(outputDir + "/InteractionZScoresMatrix-" + nrCovsRemoved + "Covariates.txt.binary");
			ExpressionDataset dataset2 = new ExpressionDataset(outputDir + "/InteractionZScoresMatrix-" + (nrCovsRemoved + 1) + "Covariates.txt.binary");

			for (int covariate = 0; covariate < dataset.nrProbes; covariate++) {
				double chi2Sum1 = 0, chi2Sum2 = 0;
				for (int gene = 0; gene < dataset.nrSamples; gene++) {
					double z_before = dataset.rawData[covariate][gene];
					chi2Sum1 += z_before * z_before;
					double z_after = dataset2.rawData[covariate][gene];
					chi2Sum2 += z_after * z_after;

				}
				System.out.println(nrCovsRemoved + "\t" + dataset.probeNames[covariate] + "\t" + chi2Sum1 + "\t" + chi2Sum2 + "\t" + (chi2Sum1 - chi2Sum2));
			}
		}
	}

	public void preprocessData() {

		HashMap hashGenotypes = new HashMap();
		HashMap hashExpression = new HashMap();
		HashMap hashEQTLs = new HashMap();
		try {
			java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(new File(inputDir + "/bigTableLude.txt")));
			String str = in.readLine();
			String[] data = str.split("\t");
			for (int d = 0; d < data.length; d++) {
				System.out.println(d + "\t" + data[d]);
				if (data[d].endsWith("_dosage")) {
					hashGenotypes.put(data[d], null);
				}
				if (data[d].endsWith("_exp")) {
					hashExpression.put(data[d], null);
				}
			}
			int itr = 0;
			while ((str = in.readLine()) != null) {
				if (!str.contains("NA")) {
					data = str.split("\t");
					hashEQTLs.put(data[0], null);
					itr++;
					if (itr % 100 == 0) {
						System.out.println(itr);
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Error:\t" + e.getMessage());
			e.printStackTrace();
		}

		ExpressionDataset datasetGenotypes = new ExpressionDataset(inputDir + "/bigTableLude.txt", "\t", hashEQTLs, hashGenotypes);
		ExpressionDataset datasetExpression = new ExpressionDataset(inputDir + "/bigTableLude.txt", "\t", hashEQTLs, hashExpression);
		datasetGenotypes.save(datasetGenotypes.fileName + ".Genotypes.binary");
		datasetExpression.save(datasetGenotypes.fileName + ".Expression.binary");

		ExpressionDataset datasetCovariates = new ExpressionDataset(inputDir + "/covariateTableLude.txt");
		datasetCovariates.save(datasetCovariates.fileName + ".Covariates.binary");
		System.exit(0);

	}

	public final String performInteractionAnalysis(String[] covsToCorrect, String[] covsToCorrect2, HashMap hashEQTLs, TextFile outputTopCovs, File snpsToSwapFile) throws IOException, Exception {

		HashMap hashSamples = new HashMap();

		if (1 == 1) {

			System.out.println("Removing outlier samples!!!");
			HashMap hashCovariates = new HashMap();
			hashCovariates.put("MEDIAN_5PRIME_BIAS", null);
			hashCovariates.put("MEDIAN_3PRIME_BIAS", null);
			ExpressionDataset datasetCovariates = new ExpressionDataset(inputDir + "/covariateTableLude.txt.Covariates.binary", "\t", hashCovariates, null);
			hashSamples = new HashMap();
			for (int s = 0; s < datasetCovariates.nrSamples; s++) {
				if (datasetCovariates.rawData[0][s] != 0) {
					hashSamples.put(datasetCovariates.sampleNames[s], null);
				}
			}
			datasetCovariates = new ExpressionDataset(inputDir + "/covariateTableLude.txt.Covariates.binary", "\t", hashCovariates, hashSamples);
			HashMap hashSamplesToExclude = new HashMap();
			if (1 == 1) {
				int index = ((Integer) datasetCovariates.hashProbes.get("MEDIAN_5PRIME_BIAS")).intValue();
				double mean = JSci.maths.ArrayMath.mean(datasetCovariates.rawData[index]);
				double stdev = JSci.maths.ArrayMath.standardDeviation(datasetCovariates.rawData[index]);
				for (int s = 0; s < datasetCovariates.nrSamples; s++) {
					double z = (datasetCovariates.rawData[index][s] - mean) / stdev;
					if (Math.abs(z) > 3) {
						hashSamplesToExclude.put(datasetCovariates.sampleNames[s], null);
					}
				}
			}
			if (1 == 1) {
				int index = ((Integer) datasetCovariates.hashProbes.get("MEDIAN_3PRIME_BIAS")).intValue();
				double mean = JSci.maths.ArrayMath.mean(datasetCovariates.rawData[index]);
				double stdev = JSci.maths.ArrayMath.standardDeviation(datasetCovariates.rawData[index]);
				for (int s = 0; s < datasetCovariates.nrSamples; s++) {
					double z = (datasetCovariates.rawData[index][s] - mean) / stdev;
					if (Math.abs(z) > 3) {
						hashSamplesToExclude.put(datasetCovariates.sampleNames[s], null);
					}
				}
			}
			hashSamples = new HashMap();
			for (int s = 0; s < datasetCovariates.nrSamples; s++) {
				if (!hashSamplesToExclude.containsKey(datasetCovariates.sampleNames[s])) {
					hashSamples.put(datasetCovariates.sampleNames[s], null);
					hashSamples.put(datasetCovariates.sampleNames[s] + "_exp", null);
					hashSamples.put(datasetCovariates.sampleNames[s] + "_dosage", null);
				}
			}
		}

		ExpressionDataset datasetGenotypes = new ExpressionDataset(inputDir + "/bigTableLude.txt.Genotypes.binary", "\t", hashEQTLs, hashSamples);
		ExpressionDataset datasetExpression = new ExpressionDataset(inputDir + "/bigTableLude.txt.Expression.binary", "\t", hashEQTLs, hashSamples);
		ExpressionDataset datasetCovariates = new ExpressionDataset(inputDir + "/covariateTableLude.txt.Covariates.binary", "\t", null, hashSamples);

		org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression regression = new org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression();
		int nrSamples = datasetGenotypes.nrSamples;


		if (1 == 1) {
			//Define a set of covariates that we want to use as correction:
			System.out.println("Correcting gene expression data for cohort specific effects and top 25 components");
			//String[] cohorts = {"LLDeep", "LLS", "RS", "CODAM"};
			int nrCompsToCorrectFor = 25;
			ExpressionDataset datasetCovariatesToCorrectFor = new ExpressionDataset(covsToCorrect2.length + nrCompsToCorrectFor, datasetGenotypes.nrSamples);
			datasetCovariatesToCorrectFor.sampleNames = datasetGenotypes.sampleNames;

			for (int i = 0; i < covsToCorrect2.length; ++i) {
				String cov = covsToCorrect2[i];
				Integer c = datasetCovariatesToCorrectFor.hashProbes.get(cov);
				if (c == null) {
					throw new Exception("Covariate not found: " + cov);
				}
				for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
					datasetCovariatesToCorrectFor.rawData[i][s] = datasetCovariates.rawData[c][s];
				}
			}

//			for (int p = 0; p < cohorts.length; p++) {
//				for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
//					if (datasetGenotypes.sampleNames[s].startsWith(cohorts[p])) {
//						datasetCovariatesToCorrectFor.rawData[p][s] = 1;
//					}
//				}
//			}
			if (nrCompsToCorrectFor > 0) {
				for (int comp = 0; comp < nrCompsToCorrectFor; comp++) {
					for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
						datasetCovariatesToCorrectFor.rawData[covsToCorrect2.length + comp][s] = datasetCovariates.rawData[datasetCovariates.nrProbes - 51 + comp][s];
					}
				}
			}

			datasetCovariatesToCorrectFor.transposeDataset();

			datasetCovariatesToCorrectFor.save(inputDir + "/CovariatesToCorrectFor.txt");
			orthogonalizeDataset(inputDir + "/CovariatesToCorrectFor.txt");
			datasetCovariatesToCorrectFor = new ExpressionDataset(inputDir + "/CovariatesToCorrectFor.txt.PrincipalComponents.txt");
			datasetCovariatesToCorrectFor.transposeDataset();
			ExpressionDataset datasetCovariatesToCorrectForEigenvalues = new ExpressionDataset(inputDir + "/CovariatesToCorrectFor.txt.Eigenvalues.txt");
			for (int snp = 0; snp < datasetExpression.nrProbes; snp++) {
				for (int cov = 0; cov < datasetCovariatesToCorrectFor.nrProbes; cov++) {
					if (datasetCovariatesToCorrectForEigenvalues.rawData[cov][0] > 1E-5) {
						double[] rc = getLinearRegressionCoefficients(datasetCovariatesToCorrectFor.rawData[cov], datasetExpression.rawData[snp]);
						for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
							datasetExpression.rawData[snp][s] -= rc[0] * datasetCovariatesToCorrectFor.rawData[cov][s];
						}
					}
				}
			}


		}





		//double[] mainEQTLCorr = new double[datasetGenotypes.nrProbes];


		if (snpsToSwapFile != null) {
			System.out.println("Enforcing for every eQTL that the genotype dosage is swapped based on: " + snpsToSwapFile.getAbsolutePath());

			HashSet<String> snpsToSwap = new HashSet<String>();
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(snpsToSwapFile), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				snpsToSwap.add(line);
			}
			reader.close();

			for (int snp = 0; snp < datasetGenotypes.nrProbes; snp++) {

				if (snpsToSwap.contains(datasetGenotypes.probeNames[snp])) {

					for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
						datasetGenotypes.rawData[snp][s] = 2 - datasetGenotypes.rawData[snp][s];
					}

				}

				//mainEQTLCorr[snp] = corr;
			}


		} else {
			System.out.println("Enforcing for every eQTL that the genotype dosage positively correlated with gene expression levels:");

			Writer writer = new BufferedWriter(new FileWriter(outputDir + "/swappedDosages.txt"));
			for (int snp = 0; snp < datasetGenotypes.nrProbes; snp++) {
				double corr = JSci.maths.ArrayMath.correlation(datasetGenotypes.rawData[snp], datasetExpression.rawData[snp]);
				//System.out.println(datasetExpression.probeNames[snp] + "\t" + snp + "\t" + corr);

				if (corr < 0) {
					corr = -corr;
					for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
						datasetGenotypes.rawData[snp][s] = 2 - datasetGenotypes.rawData[snp][s];
					}
					writer.append(datasetGenotypes.probeNames[snp]);
					writer.append('\n');
				}

				//mainEQTLCorr[snp] = corr;
			}
			writer.close();

		}

		if (1 == 1) {

			if (1 == 1) {
				System.out.println("Correcting covariate data for cohort specific effects:");
//                String[] cohorts = {"LLDeep","LLS","RS","CODAM"};
				ExpressionDataset datasetCovariatesToCorrectFor = new ExpressionDataset(covsToCorrect2.length + covsToCorrect.length, datasetGenotypes.nrSamples);
				datasetCovariatesToCorrectFor.sampleNames = datasetGenotypes.sampleNames;

				for (int i = 0; i < covsToCorrect2.length; ++i) {
					String cov = covsToCorrect2[i];
					Integer c = datasetCovariatesToCorrectFor.hashProbes.get(cov);
					if (c == null) {
						throw new Exception("Covariate not found: " + cov);
					}
					for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
						datasetCovariatesToCorrectFor.rawData[i][s] = datasetCovariates.rawData[c][s];
					}
				}

//                for (int p=0; p<cohorts.length; p++) {
//                    for (int s=0; s<datasetGenotypes.nrSamples; s++) {
//                        if (datasetGenotypes.sampleNames[s].startsWith(cohorts[p])) {
//                            datasetCovariatesToCorrectFor.rawData[p][s]=1;
//                        }
//                    }
//                }

				HashMap hashCovsToCorrect = new HashMap();
				int[] covsToCorrectIndex = new int[covsToCorrect.length];
				for (int c = 0; c < covsToCorrect.length; c++) {
					hashCovsToCorrect.put(covsToCorrect[c], null);
					covsToCorrectIndex[c] = ((Integer) datasetCovariates.hashProbes.get(covsToCorrect[c])).intValue();
					for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
						datasetCovariatesToCorrectFor.rawData[covsToCorrect2.length + c][s] = datasetCovariates.rawData[covsToCorrectIndex[c]][s];
					}
				}

				datasetCovariatesToCorrectFor.transposeDataset();

				datasetCovariatesToCorrectFor.save(inputDir + "/CovariatesToCorrectFor.txt");
				orthogonalizeDataset(inputDir + "/CovariatesToCorrectFor.txt");
				datasetCovariatesToCorrectFor = new ExpressionDataset(inputDir + "/CovariatesToCorrectFor.txt.PrincipalComponents.txt");
				datasetCovariatesToCorrectFor.transposeDataset();
				ExpressionDataset datasetCovariatesToCorrectForEigenvalues = new ExpressionDataset(inputDir + "/CovariatesToCorrectFor.txt.Eigenvalues.txt");

				for (int p = 0; p < datasetCovariates.nrProbes; p++) {
					if (!hashCovsToCorrect.containsKey(datasetCovariates.probeNames[p])) {
						for (int cov = 0; cov < datasetCovariatesToCorrectFor.nrProbes; cov++) {
							if (datasetCovariatesToCorrectForEigenvalues.rawData[cov][0] > 1E-5) {
								double[] rc = getLinearRegressionCoefficients(datasetCovariatesToCorrectFor.rawData[cov], datasetCovariates.rawData[p]);
								for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
									datasetCovariates.rawData[p][s] -= rc[0] * datasetCovariatesToCorrectFor.rawData[cov][s];
								}
							}
						}
						double stdev = JSci.maths.ArrayMath.standardDeviation(datasetCovariates.rawData[p]);
						double mean = JSci.maths.ArrayMath.mean(datasetCovariates.rawData[p]);
						if (stdev < 1E-5) {
							for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
								datasetCovariates.rawData[p][s] = mean;
							}
						}
					}
				}


			}

			if (1 == 1) {
				System.out.println("Correcting covariate data for cis-eQTL effects:");
				for (int p = 0; p < datasetCovariates.nrProbes; p++) {
					if (datasetExpression.hashProbes.containsKey(datasetCovariates.probeNames[p])) {
						int index = ((Integer) datasetExpression.hashProbes.get(datasetCovariates.probeNames[p])).intValue();
						double[] rc = getLinearRegressionCoefficients(datasetGenotypes.rawData[index], datasetCovariates.rawData[p]);
						for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
							datasetCovariates.rawData[p][s] -= rc[0] * datasetGenotypes.rawData[index][s];
						}
					}
				}
			}

			if (1 == 2) {
				datasetCovariates.save(inputDir + "/CovariatesCorrected.txt");
				HashMap hashProbesToFilter = new HashMap();
				for (int p = 0; p < datasetCovariates.nrProbes; p++) {
					if (datasetCovariates.probeNames[p].startsWith("ENSG")) {
						hashProbesToFilter.put(datasetCovariates.probeNames[p], null);
					}
				}
				ExpressionDataset datasetCovariatesCorrected = new ExpressionDataset(inputDir + "/CovariatesCorrected.txt", "\t", hashProbesToFilter, null);
				datasetCovariatesCorrected.transposeDataset();
				datasetCovariatesCorrected.save(inputDir + "/CovariatesCorrected.txt");
				System.exit(0);
			}

			if (1 == 2) {
				ExpressionDataset datasetICA = new ExpressionDataset("/Users/lude/Documents/ICA/mixingmatrix.txt");
				//ExpressionDataset datasetICA = new ExpressionDataset("/Users/lude/Documents/ICA/signals.txt");
				datasetICA.transposeDataset();
				for (int p = 0; p < datasetICA.nrProbes; p++) {
					datasetCovariates.rawData[p] = datasetICA.rawData[p];
					datasetCovariates.probeNames[p] = datasetICA.probeNames[p];
					if (p == 7) {
						for (int q = 0; q < datasetCovariates.nrProbes; q++) {
							double corr = JSci.maths.ArrayMath.correlation(datasetICA.rawData[p], datasetCovariates.rawData[q]);
							System.out.println(p + "\t" + datasetICA.probeNames[p] + "\t" + q + "\t" + datasetCovariates.probeNames[q] + "\t" + corr + "\t" + corr * corr);
						}
					}
				}

				orthogonalizeDataset("/Users/lude/Documents/ICA/mixingmatrix.txt");
				//System.exit(0);
			}

			System.out.println("Enforcing normal distribution on covariates");

			NaturalRanking ranker = new NaturalRanking();

			for (int p = 0; p < datasetCovariates.nrProbes; p++) {
				//Rank order the expression values:
				double[] values = new double[datasetCovariates.nrSamples];
				for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
					values[s] = datasetCovariates.rawData[p][s];
				}
				double[] rankedValues = ranker.rank(values);
				//Replace the original expression value with the standard distribution enforce:
				for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
					//Convert the rank to a proportion, with range <0, 1>
					double pValue = (0.5d + rankedValues[s] - 1d) / (double) (rankedValues.length);
					//Convert the pValue to a Z-Score:
					double zScore = cern.jet.stat.tdouble.Probability.normalInverse(pValue);
					datasetCovariates.rawData[p][s] = zScore; //Replace original expression value with the Z-Score
				}
			}

		}

		cern.jet.random.tdouble.engine.DoubleRandomEngine randomEngine = new cern.jet.random.tdouble.engine.DRand();

		ExpressionDataset datasetExpressionBeforeEQTLCorrection = new ExpressionDataset(datasetExpression.nrProbes, datasetExpression.nrSamples);
		for (int p = 0; p < datasetExpression.nrProbes; p++) {
			for (int s = 0; s < datasetExpression.nrSamples; s++) {
				datasetExpressionBeforeEQTLCorrection.rawData[p][s] = datasetExpression.rawData[p][s];
			}
		}

		if (1 == 1) {
			System.out.println("Correcting expression data for predefined gene environment interaction effects (GC content, Gender, 5'Median Bias, 3'Median Bias):");
			int[] covsToCorrectIndex = new int[covsToCorrect.length];
			for (int c = 0; c < covsToCorrect.length; c++) {
				covsToCorrectIndex[c] = ((Integer) datasetCovariates.hashProbes.get(covsToCorrect[c])).intValue();

			}
			for (int snp = 0; snp < datasetGenotypes.nrProbes; snp++) {
				double[][] valsX = new double[nrSamples][1 + covsToCorrect.length * 2]; //store genotypes, covariates, interactions
				for (int s = 0; s < nrSamples; s++) {
					valsX[s][0] = datasetGenotypes.rawData[snp][s]; //genotypes
				}
				for (int c = 0; c < covsToCorrect.length; c++) {
					for (int s = 0; s < nrSamples; s++) {
						valsX[s][c * 2 + 1] = datasetCovariates.rawData[covsToCorrectIndex[c]][s]; //covariate
						valsX[s][c * 2 + 2] = valsX[s][0] * valsX[s][c * 2 + 1]; //interction
					}
				}
				double[] valsY = datasetExpression.rawData[snp];
				regression.newSampleData(valsY, valsX);
				datasetExpression.rawData[snp] = regression.estimateResiduals();
				}
			}


		if (1 == 1) {
			System.out.println("Enforcing normal distribution on expression data:");

			NaturalRanking ranker = new NaturalRanking();

			for (int p = 0; p < datasetExpression.nrProbes; p++) {
				//Rank order the expression values:
				double[] values = new double[datasetExpression.nrSamples];
				for (int s = 0; s < datasetExpression.nrSamples; s++) {
					values[s] = datasetExpression.rawData[p][s];
				}

				double[] rankedValues = ranker.rank(values);
				//Replace the original expression value with the standard distribution enforce:
				for (int s = 0; s < datasetExpression.nrSamples; s++) {
					//Convert the rank to a proportion, with range <0, 1>
					double pValue = (0.5d + rankedValues[s] - 1d) / (double) (rankedValues.length);
					//Convert the pValue to a Z-Score:
					double zScore = cern.jet.stat.tdouble.Probability.normalInverse(pValue);
					datasetExpression.rawData[p][s] = zScore; //Replace original expression value with the Z-Score
				}
			}

			System.out.println("Expression data now force normal");

		}

		if (1 == 2) {
			System.out.println("WARNING: PERMUTING GENOTYPE DATA!!!!");
			String[] cohorts = {"LLDeep", "LLS", "RS", "CODAM"};
			int[] permSampleIDs = new int[datasetGenotypes.nrSamples];
			for (int p = 0; p < cohorts.length; p++) {
				Vector vecSamples = new Vector();
				for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
					if (datasetGenotypes.sampleNames[s].startsWith(cohorts[p])) {
						vecSamples.add(s);
					}
				}
				int nrSamplesThisCohort = vecSamples.size();
				for (int s = 0; s < datasetGenotypes.nrSamples; s++) {
					if (datasetGenotypes.sampleNames[s].startsWith(cohorts[p])) {
						int randomSample = ((Integer) vecSamples.remove((int) ((double) vecSamples.size() * Math.random()))).intValue();
						permSampleIDs[s] = randomSample;
					}
				}
			}
			ExpressionDataset datasetGenotypes2 = new ExpressionDataset(datasetGenotypes.nrProbes, datasetGenotypes.nrSamples);
			datasetGenotypes2.probeNames = datasetGenotypes.probeNames;
			datasetGenotypes2.sampleNames = datasetGenotypes.sampleNames;
			datasetGenotypes2.recalculateHashMaps();
			for (int p = 0; p < datasetGenotypes2.nrProbes; p++) {
				for (int s = 0; s < datasetGenotypes2.nrSamples; s++) {
					datasetGenotypes2.rawData[p][s] = datasetGenotypes.rawData[p][permSampleIDs[s]];
				}
			}
			datasetGenotypes = datasetGenotypes2;
		}


		if (1 == 1) {



			ExpressionDataset datasetZScores = new ExpressionDataset(datasetCovariates.nrProbes, datasetExpression.nrProbes);
			datasetZScores.probeNames = datasetCovariates.probeNames;
			datasetZScores.sampleNames = datasetGenotypes.probeNames;
			datasetZScores.recalculateHashMaps();



			java.util.concurrent.ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			CompletionService<DoubleArrayIntegerObject> pool = new ExecutorCompletionService<DoubleArrayIntegerObject>(threadPool);
			int nrTasks = 0;
			for (int cov = 0; cov < datasetCovariates.nrProbes; cov++) {
				double stdev = JSci.maths.ArrayMath.standardDeviation(datasetCovariates.rawData[cov]);
				if (stdev > 0) {
					PerformInteractionAnalysisPermutationTask task = new PerformInteractionAnalysisPermutationTask(datasetGenotypes, datasetExpression, datasetCovariates, cov);
					pool.submit(task);
					nrTasks++;
				}
			}

			String maxChi2Cov = "";
			double maxChi2 = 0;
			try {
				// If gene annotation provided, for chi2sum calculation use only genes that are 1mb apart
				if (geneDistanceMap != null) {
					for (int task = 0; task < nrTasks; task++) {
						try {
							DoubleArrayIntegerObject result = pool.take().get();
							int cov = result.intValue;
							double chi2Sum = 0;
							double[] covZ = datasetZScores.rawData[cov];
							for (int snp = 0; snp < datasetGenotypes.nrProbes; snp++) {
								if (genesFarAway(datasetZScores.sampleNames[snp], datasetZScores.probeNames[cov])) {
									double z = result.doubleArray[snp];
									covZ[snp] = z;
									if (!Double.isNaN(z)) {
										chi2Sum += z * z;
									}
								}
							}
							if (chi2Sum > maxChi2) {
								maxChi2 = chi2Sum;
								maxChi2Cov = datasetCovariates.probeNames[cov];
							}
							//System.out.println(covsToCorrect.length + "\t" + cov + "\t" + datasetCovariates.probeNames[cov] + "\t" + chi2Sum);
							if ((task + 1) % 512 == 0) {
								System.out.println(task + 1 + " tasks processed");
							}
						} catch (ExecutionException ex) {
							Logger.getLogger(PerformInteractionAnalysisPermutationTask.class.getName()).log(Level.SEVERE, null, ex);
						}
					}
				} //If gene annotation not provided, use all gene pairs
				else {
					for (int task = 0; task < nrTasks; task++) {
						try {
							DoubleArrayIntegerObject result = pool.take().get();
							int cov = result.intValue;
							double chi2Sum = 0;
							double[] covZ = datasetZScores.rawData[cov];
							for (int snp = 0; snp < datasetGenotypes.nrProbes; snp++) {
								double z = result.doubleArray[snp];
								covZ[snp] = z;
								if (!Double.isNaN(z)) {
									chi2Sum += z * z;
								}
							}
							if (chi2Sum > maxChi2) {
								maxChi2 = chi2Sum;
								maxChi2Cov = datasetCovariates.probeNames[cov];
							}
							//System.out.println(covsToCorrect.length + "\t" + cov + "\t" + datasetCovariates.probeNames[cov] + "\t" + chi2Sum);
							if ((task + 1) % 512 == 0) {
								System.out.println(task + 1 + " tasks processed");
							}
						} catch (ExecutionException ex) {
							Logger.getLogger(PerformInteractionAnalysisPermutationTask.class.getName()).log(Level.SEVERE, null, ex);
						}
					}
				}
				threadPool.shutdown();
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println(e.getMessage());
			}

			System.out.println("Top covariate:\t" + maxChi2 + "\t" + maxChi2Cov);
			outputTopCovs.writeln("Top covariate:\t" + maxChi2 + "\t" + maxChi2Cov);
			outputTopCovs.flush();
			datasetZScores.save(outputDir + "/InteractionZScoresMatrix-" + covsToCorrect.length + "Covariates.txt");

			return maxChi2Cov;
		}

		return null;
	}

	/**
	 * Creates a map of gene name to GenomicBoundary containing gene coordinates
	 * and the coordinate of its midpoint as annotation
	 *
	 * @param annotFname - path to the annotation file (in the
	 * eqtlmappingpipeline format)
	 * @throws IOException
	 */
	private void createGeneDistanceMap(String annotFname) throws IOException {
		System.out.println("Creating a gene distance map from " + annotFname);

		geneDistanceMap = new HashMap<String, GenomicBoundary<Integer>>();

		TextFile annotFile = new TextFile(annotFname, false);
		String els[] = annotFile.readLineElems(TextFile.tab);

		while ((els = annotFile.readLineElems(TextFile.tab)) != null) {
			int start = Integer.parseInt(els[4]), end = Integer.parseInt(els[5]), middle = start + (end - start) / 2;
			GenomicBoundary genomicboundary = new GenomicBoundary(els[3], Integer.parseInt(els[4]), Integer.parseInt(els[5]), middle);
			geneDistanceMap.put(els[1], genomicboundary);
		}
		annotFile.close();
	}

	/**
	 * Checks if the genomic distance between 2 genes is more than 1mb
	 *
	 * @param gene1
	 * @param gene2
	 * @return true if the genes are more than 1mb apart
	 */
	private boolean genesFarAway(String gene1, String gene2) {
		// if one of the covariates is a technical bias or a cell count etc
		if ((!gene1.startsWith("ENS")) || (!gene2.startsWith("ENS"))) {
			return true;
		}

		GenomicBoundary<Integer> gb1 = null, gb2 = null;
		try {
			gb1 = geneDistanceMap.get(gene1);
			gb2 = geneDistanceMap.get(gene2);

			if (gb1.getChromosome() != gb2.getChromosome()) {
				return true;
			}
			if (Math.abs(gb1.getAnnotation() - gb2.getAnnotation()) > 1000000) {
				return true;
			}
		} catch (Exception e) {
			System.out.println("Error: gene annotation doesn't contain one of these genes: " + gene1 + " or " + gene2);
			System.exit(1);
		}
		return false;
	}

	static public void orthogonalizeDataset(String inputFile) {

		ExpressionDataset dataset = new ExpressionDataset(inputFile);
		dataset.transposeDataset();
		dataset.standardNormalizeData();
		int nrVars = dataset.nrProbes;
		int nrSamples = dataset.nrSamples;

		double[][] matrix = new double[nrVars][nrSamples];
		for (int s = 0; s < nrVars; s++) {
			for (int sample = 0; sample < nrSamples; sample++) {
				matrix[s][sample] = dataset.rawData[s][sample];
			}
		}
		double[][] correlationMatrix = new double[nrVars][nrVars];
		for (int p = 0; p < nrVars; p++) {
			correlationMatrix[p][p] = 1d;
			for (int q = p + 1; q < nrVars; q++) {
				double covariance = 0;
				for (int sample = 0; sample < nrSamples; sample++) {
					covariance += matrix[p][sample] * matrix[q][sample];
				}
				covariance /= (double) (nrSamples - 1);
				correlationMatrix[p][q] = covariance;
				correlationMatrix[q][p] = covariance;
			}
		}
		Jama.EigenvalueDecomposition eig = eigenValueDecomposition(correlationMatrix);
		double[] eigenValues = eig.getRealEigenvalues();

		double[][] eigenVectors = new double[correlationMatrix.length][correlationMatrix.length];
		ExpressionDataset datasetEigenvectors = new ExpressionDataset(correlationMatrix.length, correlationMatrix.length);
		ExpressionDataset datasetEigenvalues = new ExpressionDataset(correlationMatrix.length, 2);
		for (int pca = 0; pca < correlationMatrix.length; pca++) {
			datasetEigenvectors.probeNames[pca] = "Comp" + (pca + 1);
			datasetEigenvalues.probeNames[pca] = "Comp" + (pca + 1);
			datasetEigenvectors.sampleNames[pca] = dataset.probeNames[pca];
		}
		datasetEigenvalues.sampleNames[0] = "Eigenvalues";
		datasetEigenvalues.sampleNames[1] = "ExplainedVariance";
		for (int pca = 0; pca < correlationMatrix.length; pca++) {
			datasetEigenvectors.rawData[pca] = getEigenVector(eig, pca);
			datasetEigenvalues.rawData[pca][0] = eigenValues[eigenValues.length - 1 - pca];
			datasetEigenvalues.rawData[pca][1] = getEigenValueVar(eigenValues, pca);
			System.out.println(pca + "\tExplainedVariance:\t" + getEigenValueVar(eigenValues, pca) + "\tEigenvalue:\t" + eigenValues[eigenValues.length - 1 - pca]);
		}
		datasetEigenvectors.transposeDataset();
		datasetEigenvectors.save(inputFile + ".Eigenvectors.txt");
		datasetEigenvalues.save(inputFile + ".Eigenvalues.txt");

		//Calculate principal components:
		ExpressionDataset datasetPCs = new ExpressionDataset(dataset.nrSamples, correlationMatrix.length);
		for (int pca = 0; pca < correlationMatrix.length; pca++) {
			datasetPCs.sampleNames[pca] = "Comp" + (pca + 1);
		}
		for (int p = 0; p < datasetPCs.nrProbes; p++) {
			datasetPCs.probeNames[p] = dataset.sampleNames[p];
		}
		for (int pca = 0; pca < correlationMatrix.length; pca++) {
			for (int p = 0; p < dataset.nrProbes; p++) {
				for (int s = 0; s < dataset.nrSamples; s++) {
					datasetPCs.rawData[s][pca] += datasetEigenvectors.rawData[p][pca] * dataset.rawData[p][s];
				}
			}
		}
		datasetPCs.save(dataset.fileName + ".PrincipalComponents.txt");

		ExpressionDataset datasetFactorloadings = new ExpressionDataset(correlationMatrix.length, correlationMatrix.length);
		datasetPCs.transposeDataset();
		for (int p = 0; p < dataset.nrProbes; p++) {
			datasetFactorloadings.probeNames[p] = dataset.probeNames[p];
		}
		for (int pca = 0; pca < datasetPCs.nrProbes; pca++) {
			datasetFactorloadings.sampleNames[pca] = "Comp" + (pca + 1);
			for (int p = 0; p < dataset.nrProbes; p++) {
				datasetFactorloadings.rawData[p][pca] = JSci.maths.ArrayMath.correlation(datasetPCs.rawData[pca], dataset.rawData[p]);
			}
		}
		datasetFactorloadings.save(dataset.fileName + ".Factorloadings.txt");

	}

	static public ExpressionDataset orthogonalizeMatrix(ExpressionDataset dataset) {

		dataset.standardNormalizeData();
		int nrVars = dataset.nrProbes;
		int nrSamples = dataset.nrSamples;
		double[][] matrix = new double[nrVars][nrSamples];
		for (int s = 0; s < nrVars; s++) {
			for (int sample = 0; sample < nrSamples; sample++) {
				matrix[s][sample] = dataset.rawData[s][sample];
			}
		}
		double[][] correlationMatrix = new double[nrVars][nrVars];
		for (int p = 0; p < nrVars; p++) {
			correlationMatrix[p][p] = 1d;
			for (int q = p + 1; q < nrVars; q++) {
				double covariance = 0;
				for (int sample = 0; sample < nrSamples; sample++) {
					covariance += matrix[p][sample] * matrix[q][sample];
				}
				covariance /= (double) (nrSamples - 1);
				if (covariance > 1) {
					covariance = 1d;
				}
				if (covariance < -1) {
					covariance = -1d;
				}
				correlationMatrix[p][q] = covariance;
				correlationMatrix[q][p] = covariance;
			}
		}
		Jama.EigenvalueDecomposition eig = eigenValueDecomposition(correlationMatrix);
		double[] eigenValues = eig.getRealEigenvalues();
		int nrCompsWithPositiveEigenvalues = 0;
		for (int e = 0; e < eigenValues.length; e++) {
			//System.out.println(e + "\t" + eigenValues[e]);
			if (eigenValues[e] > 1e-10) {
				nrCompsWithPositiveEigenvalues++;
			}
		}

		ExpressionDataset datasetEigenvectors = new ExpressionDataset(correlationMatrix.length, correlationMatrix.length);
		for (int pca = 0; pca < correlationMatrix.length; pca++) {
			datasetEigenvectors.rawData[pca] = getEigenVector(eig, pca);
		}
		datasetEigenvectors.transposeDataset();

		//Calculate principal components:
		ExpressionDataset datasetPCs = new ExpressionDataset(dataset.nrSamples, nrCompsWithPositiveEigenvalues);
		for (int pca = 0; pca < nrCompsWithPositiveEigenvalues; pca++) {
			datasetPCs.sampleNames[pca] = "Comp" + (pca + 1);
		}
		for (int p = 0; p < datasetPCs.nrProbes; p++) {
			datasetPCs.probeNames[p] = dataset.sampleNames[p];
		}
		for (int pca = 0; pca < nrCompsWithPositiveEigenvalues; pca++) {
			for (int p = 0; p < dataset.nrProbes; p++) {
				for (int s = 0; s < dataset.nrSamples; s++) {
					datasetPCs.rawData[s][pca] += datasetEigenvectors.rawData[p][pca] * dataset.rawData[p][s];
				}
			}
		}
		datasetPCs.transposeDataset();
		return datasetPCs;

	}

	static public double[] getLinearRegressionCoefficients(double[] xVal, double[] yVal) {
		double n = (double) xVal.length;
		double sumX = 0;
		double sumXX = 0;
		double sumY = 0;
		double sumXY = 0;
		for (int x = 0; x < xVal.length; x++) {
			sumX += xVal[x];
			sumXX += xVal[x] * xVal[x];
			sumY += yVal[x];
			sumXY += xVal[x] * yVal[x];
		}
		double sXX = sumXX - sumX * sumX / n;
		double sXY = sumXY - sumX * sumY / n;
		double a = sXY / sXX;
		double b = (sumY - a * sumX) / n;
		double[] regressionCoefficients = new double[2];
		regressionCoefficients[0] = a;
		regressionCoefficients[1] = b;
		return regressionCoefficients;
	}

	static public Jama.EigenvalueDecomposition eigenValueDecomposition(double[][] data) {
		Jama.Matrix m = new Jama.Matrix(data);
		Jama.EigenvalueDecomposition eig = m.eig();
		return eig;
	}

	static public double[] getEigenVector(Jama.EigenvalueDecomposition eig, double[] eigenValues, int pca) {
		Jama.Matrix eigenValueMatrix = eig.getV();
		double[][] eigenValueMat = eigenValueMatrix.getArray();
		double[] eigenVector = new double[eigenValueMat.length];
		for (int i = 0; i < eigenValueMat.length; i++) {
			eigenVector[i] = eigenValueMat[i][eigenValueMat.length - 1 - pca]; // * Math.sqrt(eigenValues[eigenValues.length - 1 - pca]);
		}
		return eigenVector;
	}

	static public double[] getEigenVector(Jama.EigenvalueDecomposition eig, int pca) {
		Jama.Matrix eigenValueMatrix = eig.getV();
		double[][] eigenValueMat = eigenValueMatrix.getArray();
		double[] eigenVector = new double[eigenValueMat.length];
		for (int i = 0; i < eigenValueMat.length; i++) {
			eigenVector[i] = eigenValueMat[i][eigenValueMat.length - 1 - pca]; // * Math.sqrt(eigenValues[eigenValues.length - 1 - pca]);
		}
		return eigenVector;
	}

	static public double getEigenValueVar(double[] eigenValues, int pca) {
		double sumEigenvalues = 0.0;
		for (Double d : eigenValues) {
			sumEigenvalues += Math.abs(d);
		}
		double result = eigenValues[eigenValues.length - 1 - pca] / sumEigenvalues;
		return result;
	}

	static public double[] getEigenVectorSVD(Jama.SingularValueDecomposition svd, double[] singularValues, int pca) {
		Jama.Matrix eigenValueMatrix = svd.getV();
		double[][] eigenValueMat = eigenValueMatrix.getArray();
		double[] eigenVector = new double[eigenValueMat.length];
		for (int i = 0; i < eigenValueMat.length; i++) {
			eigenVector[i] = eigenValueMat[i][pca] * Math.sqrt(singularValues[pca]);
		}
		return eigenVector;
	}
}