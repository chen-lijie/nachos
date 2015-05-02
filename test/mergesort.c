#include "stdlib.h"
#include "stdio.h"
//input output [tmpid]
//input can be the same as output
//use at most 2*len-2 ids
int main(int argc,char ** argv){
	int workingid=0;
	if (argc>3){
		workingid=atoi(argv[3]);
	}
	char * countlines="linecount.coff";
	char * cp="cp";
	char * splitints="splitints.coff";
	char * mergesort="mergesort.coff";
	char * mergeints="mergeints.coff";
	char * call1[2]={countlines,argv[1]};
	char * call2[3]={cp,argv[1],argv[2]};
	char buf1[16],buf2[16];
	char * call3[4]={splitints,argv[1],buf1,buf2};
	char buf3[16],buf4[16];
	char * call4[4]={mergesort,buf1,buf1,buf3};
	char * call5[4]={mergesort,buf2,buf2,buf4};
	char * call6[4]={mergeints,buf1,buf2,argv[2]};
	int lines,tmp;
	int pid=exec(countlines,2,call1);
	join(pid,&lines);
	printf("pid %d lines %d\n",pid,lines);
	if (lines<=1){
		if (strcmp(argv[1],argv[2])){
			join(exec(cp,3,call2),&tmp);
		}
	}else{
		int n1=(lines+1)/2;
		int n2=(lines)/2;
		int wid1=workingid;
		int wid2=workingid+1+n1*2-2;
		sprintf(buf1,"%d.out",wid1);
		sprintf(buf2,"%d.out",wid2);
		sprintf(buf3,"%d",wid1+1);
		sprintf(buf4,"%d",wid2+2);
		join(exec(splitints,4,call3),&tmp);
		int pid1=exec(mergesort,4,call4);
		int pid2=exec(mergesort,4,call5);
		join(pid1,&tmp);
		join(pid2,&tmp);
		join(exec(mergeints,4,call6),&tmp);
		unlink(buf1);
		unlink(buf2);
	}
	return 0;
}
