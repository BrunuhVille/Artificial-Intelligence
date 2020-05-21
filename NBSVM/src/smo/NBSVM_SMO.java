package smo;

//�Զ����࣬�൱�ڽṹ��
class Node{
	int idx;
	double f;
	public Node(int idx,double f) {
		this.idx=idx;
		this.f=f;
	}
} 


public class NBSVM_SMO {
	//SMO�Ĳ�
	private double[][] data;
	private Integer[] label;
	private double C;
	//kkt�������������
	private double tolKKT = 1e-3;
	//ѡ���ʱ����������
	private double svTol=1.4901*Math.pow(10, -8);
	private double eps=svTol*svTol;
	//����Υ��kkt�����Ħ�����
	private int acceptedKKTviolations;
	//����������
	private int maxiter = 150000;
	private int KernelCacheLimit=5000;
	private double[][] fullKernel=null;
	private double[] kernelDiag;
	private double[] alphas;
	private double[] Gi;
	//boxconstraint�����½磬P��unequal cost�����Ǵ�������룬������Ϊ�±�0����ʾ������
	private double[] boxconstraint = new double[2];
	private double b=0;
	private String kernel;
	private double w1,w2;
	private boolean isNBSVM;
	private int[] upMask;
	private int[] downMask;
	
	//createKernelCache��ز���
	private int s;
	private int[] leastIndices;
	private double[][] subKernel;
	private int[] subKernelIndices;
	
	//rfb��gamma
	private double sigma;
	//�������������಻Ϊ-1��1����¼ԭʼ���
	private Integer positive=null;
	private Integer negative=null;
	
	//KKTviolationsLevel ����Υ��kkt���������İٷֱ� ���� 0.05��ʾ�ٷ�֮��
	//nbsvm��Ŀ��������
	public NBSVM_SMO(double[][]data,Integer[] label,double KKTviolationsLevel,Double C, String kernel,double sigma) {
		this.isNBSVM=true;
		this.data = data;
		this.label = label.clone();
		this.C = C;
		this.acceptedKKTviolations = (int)KKTviolationsLevel*label.length;
		this.kernel = kernel;
		this.alphas = new double[data.length];
		this.Gi = new double[data.length];
		this.upMask = new int[data.length];
		this.downMask = new int[data.length];
		this.sigma = sigma;
		createKernelCache();
		SetBoxAndP();
		start();
	}
	//unbsvm, Ŀ���Ǹ��࣬w1��w2��ʾ������Ͷ�����ĳͷ�����
	public NBSVM_SMO(double[][]data,Integer[] label,double KKTviolationsLevel,Double C,double w1,double w2, String kernel,double gamma) {
		this.isNBSVM=false;
		this.data = data;
		this.label = label.clone();
		this.C = C;
		this.w1 = w1;
		this.w2 = w2;
		this.acceptedKKTviolations = (int)KKTviolationsLevel*label.length;
		this.kernel = kernel;
		this.alphas = new double[data.length];
		this.Gi = new double[data.length];
		this.upMask = new int[data.length];
		this.downMask = new int[data.length];
		this.sigma = gamma;
		turn_label();
		createKernelCache();
		SetBoxAndP();
		start();
	}
	
	private void turn_label() {
		Integer p;
		for(int i=0;i<label.length;i++) {
			if(negative==null)negative=label[i];
			if(label[i]!=negative) {positive=label[i];break;}
		}
		if(negative>positive) {p=positive;positive=negative;negative=p;}
		for(int i=0;i<label.length;i++) {
			if(label[i]==negative)label[i]=-1;
			else label[i]=1;
		}
	}
	
	//����boxconstraint��P�Լ�Gi
	private void SetBoxAndP() {
		double[] P = new double[2];
		double p=0;
		for(int i=0;i<label.length;i++) {
			if(label[i]==1) {p++;upMask[i]=1;}
			else downMask[i]=1;
		}
		if(p>data.length/2) {
			double w;
			w=w1;
			w1=w2;
			w2=w;
		}
		boxconstraint[0] = C*label.length/p;
		boxconstraint[1] = C*label.length/(label.length-p);
		if(isNBSVM) {
			P[0]=p/data.length;
			P[1]=1-P[0];
		}else {
			double p0,p1;
			p0=w1*p/label.length;
			p1=w2*(label.length-p)/label.length;
			P[0]=p0/(p0+p1);
			P[1]=p1/(p0+p1);
		}
		for(int i=0;i<label.length;i++) {
			if(label[i]==1) Gi[i]=P[0];
			else Gi[i]=P[1];
		}
		//System.out.println("P:"+Arrays.toString(P));
	}
	

	//����smo�㷨��ʹ�æ���b��Ϊ���ս�
	private void start() {
		//System.out.println(Arrays.toString(Gi));
		int iter=0;
		int idx1;
		double val1=0;
		Node alpha2;
		int kktViolationCount;
		int[] flags;
		//����Ѱ��idx1,��ÿ��ѭ����ʼ��ʱ��Ҫ��idx=-1,��ʾҪ�ҵ�һ�����������Ħ�1�±�;
		while(iter<maxiter) {
			idx1=-1;
			for(int i=0;i<upMask.length;i++) {
				if(upMask[i]==1) {
					if(idx1==-1) {idx1=i;val1=label[i]*Gi[i];}
					else if(label[i]*Gi[i]>val1) {idx1=i;val1=label[i]*Gi[i];}
				}
			}
			alpha2=getMaxGain(idx1);
			if(alpha2.idx==-1) {
				for(int i=0;i<downMask.length;i++) {
					if(downMask[i]==1) {
						if(alpha2.idx==-1) {alpha2.idx=i;alpha2.f=label[i]*Gi[i];}
						else if(label[i]*Gi[i]<alpha2.f) {alpha2.idx=i;alpha2.f=label[i]*Gi[i];}
					}
				}
			}
			
			if(val1-alpha2.f<=tolKKT) {
				b=(val1+alpha2.f)/2;
				break;
			}
			updateAlphas(idx1, alpha2.idx);
			if(iter%500==0) {
				idx1=-1;
				for(int i=0;i<upMask.length;i++) {
					if(upMask[i]==1) {
						if(idx1==-1) {idx1=i;val1=label[i]*Gi[i];}
						else if(label[i]*Gi[i]>val1) {idx1=i;val1=label[i]*Gi[i];}
					}
				}
				alpha2.idx=-1;
				if(alpha2.idx==-1) {
					for(int i=0;i<downMask.length;i++) {
						if(downMask[i]==1) {
							if(alpha2.idx==-1) {alpha2.idx=i;alpha2.f=label[i]*Gi[i];}
							else if(label[i]*Gi[i]<alpha2.f) {alpha2.idx=i;alpha2.f=label[i]*Gi[i];}
						}
					}
				}
				b=(val1+alpha2.f)/2;
				flags=checkKKT();
				kktViolationCount=0;
				for(int i=0;i<flags.length;i++)if(flags[i]==0)kktViolationCount +=1;
				if(acceptedKKTviolations>0&& kktViolationCount<=acceptedKKTviolations)break;
			}
			iter++;
		}
	}
	//���ݦ�1��æ�2
	private Node getMaxGain(int idx1) {
		int idx2=0;
		double val2;
		int p=0;
		int[] mask = new int [downMask.length];
		double val1 = label[idx1]*Gi[idx1];
		for(int i=0;i<mask.length;i++)if((downMask[i]==1)&&(label[i]*Gi[i]<val1)) {mask[i]=1;p++;}
		double[] gainNumerator = new double[p];
		double[] gainDenominator = new double[p];
		int[] idx = new int[p];
		double max,flag;
		double[] kerDiag = getKernelDiag();
		p=0;
		for(int i=0;i<mask.length;i++) {
			if(mask[i]==1) {
				gainNumerator[p] = (label[i]*Gi[i]-val1)*(label[i]*Gi[i]-val1);
				idx[p] = i;
				p++;	
				}
			}
		p=0;
		if(fullKernel==null) {
			double[] kerCol1;
			kerCol1=getColumn(idx1);
			for(int i=0;i<mask.length;i++) {
				if(mask[i]==1) {
					gainDenominator[p] = -4*kerCol1[i]+2*kerDiag[i]+2*kerDiag[idx1];
					p++;	
					}
				}
		}
		else {
			for(int i=0;i<mask.length;i++) {
				if(mask[i]==1) {
					gainDenominator[p] = -4*fullKernel[i][idx1]+2*kerDiag[i]+2*kerDiag[idx1];
					p++;
					}
				}
		}
		if(p==0)return new Node(-1,-1);
		max=gainNumerator[0]/gainDenominator[0];
		for(int i=1;i<gainNumerator.length;i++) {
			flag=gainNumerator[i]/gainDenominator[i];
			if(flag>max) {max=flag;idx2=i;}
		}
		idx2=idx[idx2];
		val2=label[idx2]*Gi[idx2];
		
		return new Node(idx2,val2);
	}
	
	private void updateAlphas(int idx1, int idx2) {
		//System.out.println(Arrays.toString(Gi));
		//���¦��ԣ�������±��Ƿ��������������
		double eta;
		double low;
		double high;
		double lambda;
		double alpha_j;
		double alpha_i;
		double psi_l;
		double psi_h;
		int s;
		double fi;
		double fj;
		double Li;
		double Hi;
		double idx1_boxconstraint;
		double idx2_boxconstraint;
		if(label[idx1]==1)idx1_boxconstraint = boxconstraint[0];
		else idx1_boxconstraint = boxconstraint[1];
		if(label[idx2]==1)idx2_boxconstraint = boxconstraint[0];
		else idx2_boxconstraint = boxconstraint[1];
		if(fullKernel==null) {
			double[] K = getElements(idx1,idx2);
			eta=K[0]+K[1]-2*K[2];
		}
		else eta=fullKernel[idx1][idx1]+fullKernel[idx2][idx2]-2*fullKernel[idx1][idx2];
		if(label[idx1]==label[idx2]) {
			low = Math.max(0, alphas[idx1]+alphas[idx2]-idx1_boxconstraint);
			high = Math.min(idx2_boxconstraint, alphas[idx1]+alphas[idx2]);
		}else {
			low = Math.max(0, alphas[idx2]-alphas[idx1]);
			high = Math.min(idx2_boxconstraint, idx1_boxconstraint+alphas[idx2]-alphas[idx1]);
		}
		if(eta>eps) {
			lambda = -label[idx1]*Gi[idx1]+label[idx2]*Gi[idx2];
			alpha_j=alphas[idx2]+label[idx2]/eta*lambda;
			if(alpha_j<low)alpha_j=low;
			else if(alpha_j>high)alpha_j=high;
		}
		else {
			double[] K = getElements(idx1,idx2);
			s=label[idx1]*label[idx2];
			fi=-Gi[idx1]-alphas[idx1]*K[0]-s*alphas[idx2]*K[1];
			fj=-Gi[idx2]-alphas[idx2]*K[1]-s*alphas[idx1]*K[2];
			Li=alphas[idx1]+s*(alphas[idx2]-low);
			Hi=alphas[idx1]+s*(alphas[idx2]-high);
			psi_l=Li*fi+low*fj+Li*Li*K[0]/2+low*low*K[1]/2+s*low*Li*K[2];
			psi_h=Hi*fi+high*fj+Hi*Hi*K[0]/2+high*high*K[1]/2+s*high*Hi*K[2];
			
			if(psi_l<(psi_h-eps))alpha_j=low;
			else if(psi_l>(psi_h+eps))alpha_j=high;
			else alpha_j=alphas[idx2];
		}
		alpha_i = alphas[idx1]+label[idx2]*label[idx1]*(alphas[idx2]-alpha_j);
		if(alpha_i<eps)alpha_i=0;
		else if(alpha_i>(idx1_boxconstraint-eps))alpha_i = idx1_boxconstraint;
		if(fullKernel==null) {
			double[] kerCol1;
			double[] kerCol2;
			double idx1_change=alpha_i - alphas[idx1];
			double idx2_change=alpha_j - alphas[idx2];
			kerCol1=getColumn(idx1);
			kerCol2=getColumn(idx2);
			for(int i=0;i<Gi.length;i++)Gi[i] = Gi[i]-(kerCol1[i]*label[i])*label[idx1]*idx1_change-(kerCol2[i]*label[i])*idx2_change*label[idx2];
		}
		else {
			double[] kerCol1 = new double[fullKernel.length];
			double[] kerCol2 = new double[fullKernel.length];
			double idx1_change=alpha_i - alphas[idx1];
			double idx2_change=alpha_j - alphas[idx2];
			for(int i=0;i<fullKernel.length;i++) {kerCol1[i]=fullKernel[i][idx1];kerCol2[i]=fullKernel[i][idx2];}
			for(int i=0;i<Gi.length;i++)Gi[i] = Gi[i]-(kerCol1[i]*label[i])*label[idx1]*idx1_change-(kerCol2[i]*label[i])*idx2_change*label[idx2];
			//System.out.println(Arrays.toString(Gi));
		}
		if(label[idx1]==1) {
			if(label[idx1]*alpha_i<idx1_boxconstraint-svTol)upMask[idx1]=1;else upMask[idx1]=0;
			if(label[idx1]*alpha_i>svTol)downMask[idx1]=1;else downMask[idx1]=0;
		}
		else {
			if(label[idx1]*alpha_i<-svTol)upMask[idx1]=1;else upMask[idx1]=0;
			if(label[idx1]*alpha_i>svTol-idx1_boxconstraint)downMask[idx1]=1;else downMask[idx1]=0;
		}
		if(label[idx2]==1) {
			if(label[idx2]*alpha_j<idx2_boxconstraint-svTol)upMask[idx2]=1;else upMask[idx2]=0;
			if(label[idx2]*alpha_j>svTol)downMask[idx2]=1;else downMask[idx2]=0;
		}
		else {
			if(label[idx2]*alpha_j<-svTol)upMask[idx2]=1;else upMask[idx2]=0;
			if(label[idx2]*alpha_j>svTol-idx2_boxconstraint)downMask[idx2]=1;else downMask[idx2]=0;
		}
		alphas[idx1] = alpha_i;
        alphas[idx2] = alpha_j;
	}
	
	private int[] checkKKT() {
		double[] amount = new double[data.length];
		int[] flags = new int[data.length];
		double boxConstraint;
		for(int i=0;i<amount.length;i++)amount[i] = label[i]*b-Gi[i];
		for(int i=0;i<alphas.length;i++) {
			if(label[i]==1)boxConstraint=boxconstraint[0];else boxConstraint=boxconstraint[1];
			if(alphas[i]>svTol && (alphas[i]<boxConstraint-svTol))
				if(Math.abs(amount[i])<tolKKT)flags[i]=1;
		}
		for(int i=0;i<alphas.length;i++) {
			if(alphas[i]<svTol)
				if(amount[i]>-tolKKT)flags[i]=1;
		}
		for(int i=0;i<alphas.length;i++) {
			if(label[i]==1)boxConstraint=boxconstraint[0];else boxConstraint=boxconstraint[1];
			if(boxConstraint-alphas[i]<svTol)
				if(amount[i]<=tolKKT)flags[i]=1;
		}
		return flags;
	}
	
	private void createKernelCache() {
		if(data.length<=KernelCacheLimit) {
			this.fullKernel = new double [data.length][data.length];
			for(int i=0;i<data.length;i++)for(int j=0;j<data.length;j++)fullKernel[i][j] = kernel(data[i],data[j],kernel);
		}
		else {
			this.kernelDiag = new double[data.length];
			for(int i=0;i<data.length;i++)kernelDiag[i] = kernel(data[i],data[i],kernel);
			s=(int)Math.max(1, Math.floor(KernelCacheLimit*KernelCacheLimit/data.length));
			leastIndices = new int[s];
			subKernel = new double [data.length][s];
			subKernelIndices = new int [data.length];
		}
	}
	
	private int loadColumn(int idx) {
		int deleteIndex = leastIndices[s];
		int subKernelIndex;
		int max_subKernelIndices=subKernelIndices[0];
		if(deleteIndex==0) {
			for(int i=0;i<subKernelIndices.length;i++)if(subKernelIndices[i]>max_subKernelIndices)max_subKernelIndices=subKernelIndices[i];
			subKernelIndex = 1+max_subKernelIndices;
		}
		else {
			subKernelIndex = subKernelIndices[deleteIndex];
			subKernelIndices[deleteIndex] = 0;
		}
		subKernelIndices[idx] = subKernelIndex;
		for(int i=0;i<data.length;i++)subKernel[i][subKernelIndex] = kernel(data[i],data[idx],kernel);
		for(int i=1;i<leastIndices.length;i++)leastIndices[i] = leastIndices[i-1];
		leastIndices[0] = idx;
		return subKernelIndex;
	}
	
	private double[] getElements(int i,int j) {
		double Kii,Kjj,Kij;
		double[] result = new double[3];
		if(fullKernel!=null) {
			Kii = fullKernel[i][i];
			Kjj = fullKernel[j][j];
			Kij = fullKernel[i][j];
		}
		else {
			Kii = kernelDiag[i];
			Kjj = kernelDiag[j];
			if (subKernelIndices[i]>0)Kij = subKernel[j][ subKernelIndices[i]];
			else if(subKernelIndices[j]>0)Kij = subKernel[i][subKernelIndices[j]];
			else Kij = subKernel[i][loadColumn(j)];
		}
		result[0]=Kii;
		result[1]=Kjj;
		result[2]=Kij;
		return result;
	}
	
	private double[] getColumn(int colIdx) {
		double[] kerCol;
		if(fullKernel!=null) {
			kerCol = new double[fullKernel.length];
			for(int i=0;i<fullKernel.length;i++)kerCol[i] = fullKernel[i][colIdx];
		}
		else{
			kerCol = new double[subKernel.length];
			if(subKernelIndices[colIdx] == 0) {
			int p = loadColumn(colIdx);
			for(int i=0;i<fullKernel.length;i++)kerCol[i] = subKernel[i][p];
			}
			else {
			int p = subKernelIndices[colIdx];
			for(int i=0;i<fullKernel.length;i++)kerCol[i] = subKernel[i][p];
			}
		}
		return kerCol;
	}
	
	private double[] getKernelDiag() {
		double[] ret = new double[fullKernel.length];
		if(fullKernel!=null) {
			for(int i=0;i<fullKernel.length;i++)ret[i] = fullKernel[i][i];
		}
		else {
			ret = kernelDiag;
		}
		return ret;
	}
	
	//����������������Ԥ��,���ﻹû��ȷ��������ڳ�ƽ������ô����,�ݶ�����-1��һ��;
	public int predict(double[] unkonwn) {
		double result=b;
		for(int i=0;i<data.length;i++) {
			result +=alphas[i]*label[i]*kernel(unkonwn,data[i],kernel);
		}
		if(result>0)return positive;
		else return negative;
	}
	
	
	//���������������ľ���˻�
	private double matrix_multiply(double[] x,double[] y) {
		//�洢����˷��Ľ��
		double p = 0.0;
		for(int i=0;i<x.length;i++)p+=x[i]*y[i];
		return p;
	}
	
    //�˺���,Ĭ��gammaΪ��������֮һ
	private double kernel(double[] x1,double[] x2,String k) {
		//����˺����Ľ��
		double K=0.0;
		//�����ں�
		if(k.equals("line")){
			K = matrix_multiply(x1,x2);
		}
		//��˹�ˣ�Ŀǰ��Ĭ��gamma=1/��������
		else if(k.equals("rbf")) {
			double[] x = new double[x1.length]; 
			for(int i=0;i<x.length;i++)x[i] = x1[i]-x2[i];
			K=Math.exp(-calculate(x)/(2*sigma*sigma));
			//2*sigma*sigma,x1.length
		}
		return K;
	}
	
	//����2-��ʽ��ƽ��
	private double calculate(double[] x1) {
		double result = 0;
		for(int i=0;i<x1.length;i++)result += x1[i]*x1[i];
		return result;
	}
}
