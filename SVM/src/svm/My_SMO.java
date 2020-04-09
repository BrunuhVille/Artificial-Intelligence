package svm;


public class My_SMO {
	//SMO�Ĳ�
	private double[][] data;
	private Integer[] label;
	private double C;
	private double toler;
	private double[][] kernel_mat;
	private double[] alphas;
	private double b;
	private String kernel;
	
	public My_SMO(double[][]data,Integer[] label,Double C,Double toler,String kernel) {
		this.data = data;
		this.label = label;
		this.C = C;
		this.toler = toler;
		this.kernel = kernel;
		this.kernel_mat = new double [data.length][data.length];
		for(int i=0;i<data.length;i++)for(int j=0;j<data.length;j++)kernel_mat[i][j] = kernel(data[i],data[j],kernel);
		this.alphas = new double[data.length];
		this.b=0;
		start();
	}
	
	
	//�������
	private double calEi(int j) {
		double p = 0;
		for(int i=0;i<data.length;i++)p += alphas[i]*label[i]*kernel_mat[i][j];
		p=p+b-label[j];
		return p;
	}
	
	private double clipalpha(int a1, int a2) {
		double L;
		double H;
		double new_a2;
		double eta;
		eta=(kernel_mat[a1][a1]+kernel_mat[a2][a2]-2*kernel_mat[a1][a2]);
		if(eta==0)return 0;
		new_a2 = alphas[a2]+label[a2]*(calEi(a1)-calEi(a2))/(kernel_mat[a1][a1]+kernel_mat[a2][a2]-2*kernel_mat[a1][a2]);
		if(label[a1]==label[a2]) {
			L = Math.max(0, alphas[a2]+alphas[a1]-C);
			H = Math.min(C, alphas[a2]+alphas[a1]);
		}else {
			L = Math.max(0, alphas[a2]-alphas[a1]);
			H = Math.min(C, C+alphas[a2]-alphas[a1]);
		}
		if(new_a2>H)new_a2 = H;
		else if(new_a2<L)new_a2 = L;
		return new_a2;
	}
	//ѡ��ڶ�����,�����һ�������±꣬�����ڶ��������±�
	private int get_alpha2(int a1) {
		int p = -1;
		double distance=-1;
		for(int i=0;i<data.length;i++) {
			if(i==a1)continue;
			if(Math.abs(clipalpha(a1,i)-alphas[i])>distance) {
				p=i;
				distance = Math.abs(clipalpha(a1,i)-alphas[i]);
				}
		}
		return p;
	}
	
	private int innerL(int i) {
		double old_alpha1;
		double old_alpha2;
		int alpha2;
		double b1,b2;
		double old_E1;
		double old_E2;
		old_E1 = calEi(i);
		if((alphas[i]<C&&old_E1*label[i]<-toler) ||(alphas[i]>0&&old_E1*label[i]>toler)) {
			old_alpha1 = alphas[i];
			alpha2 = get_alpha2(i);
			old_alpha2 = alphas[alpha2];
			old_E2 = calEi(alpha2);
			alphas[alpha2] = clipalpha(i,alpha2);
			if(Math.abs(alphas[alpha2]-old_alpha2)>=0.00001) {
				alphas[i] = alphas[i]+label[i]*label[alpha2]*(old_alpha2-alphas[alpha2]);
				b1 = -old_E1+(old_alpha1-alphas[i])*label[i]*kernel_mat[i][i]+(old_alpha2-alphas[alpha2])*label[alpha2]*kernel_mat[alpha2][i]+b;
				b2 = -old_E2+(old_alpha1-alphas[i])*label[i]*kernel_mat[i][alpha2]+(old_alpha2-alphas[alpha2])*label[alpha2]*kernel_mat[alpha2][alpha2]+b;
				if(alphas[i]>0&&alphas[i]<C)b=b1;
				else if(alphas[alpha2]>0&&alphas[alpha2]<C)b=b2;
				else b=(b1+b2)/2.0;
				return 1;
			}
			return 0;
		}
		return 0;
	}
	
	
	//����smo�㷨��ʹ�æ���b��Ϊ���ս�
	private void start() {
		
		int iter=0;
		int maxiter = 4000;
		boolean flag = true;
		double alphaschanged = 0;
		//��ʼ�˺����õ��ľ���
		
		while((alphaschanged>0 || flag) && iter<maxiter) {
			//System.out.println(iter);
			alphaschanged=0;
			if(flag==true) {
				for(int i=0;i<data.length;i++) {
					alphaschanged += innerL(i);
					//if(i%100==0)System.out.println("full set loop, iter: "+i+", alphapairschanged: "+alphaschanged+", iterNum:"+iter);
				}
				iter++;
			}else {
				for(int i=0;i<data.length;i++) {
					if(alphas[i]>0&&alphas[i]<C) {
						alphaschanged += innerL(i);
						//if(i%100==0)System.out.println("non-bound set loop: "+i+", alphapairschanged: "+alphaschanged+", iterNum:"+iter);
					}
				}
				iter++;
			}
			if(flag==true)flag = false;
			else if(alphaschanged==0) flag = true;
		}
		//System.out.println(Arrays.toString(alphas));
		//System.out.println(b);
	}


	//����������������Ԥ��,���ﻹû��ȷ��������ڳ�ƽ������ô����,�ݶ�����-1��һ��;
	public int predict(double[] unkonwn) {
		double result=b;
		for(int i=0;i<data.length;i++) {
			result +=alphas[i]*label[i]*kernel(unkonwn,data[i],kernel);
		}
		if(result>0)return 1;
		else return -1;
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
			K=Math.exp(-calculate(x)/x1.length);
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