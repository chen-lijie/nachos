#include "stdlib.h"
int freadint(int fin,int * ret){
	int s=0,c,t=0;
	while (1){
		c=fgetc(fin);
		if (c<=0)
			break;
		if (c>='0' && c<='9'){
			t=t*10+(c-'0');
			s++;
		}else if (s>0){
			break;
		}
	}
	if (!s)
		return 0;
	ret[0]=t;
	return 1;
}
int main(int argc,char ** argv){
	int f1=open(argv[1]);
	int f2=creat(argv[2]);
	int f3=creat(argv[3]);
	int ft;
	int t;
	while (freadint(f1,&t)){
		fprintf(f2,"%d\n",t);
		ft=f2;
		f2=f3;
		f3=ft;
	}
	close(f1);
	close(f2);
	close(f3);
	return 0;
}
