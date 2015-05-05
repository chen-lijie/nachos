#include "syscall.h"
void asserteq(int a,int b,int line){
	if (a!=b){
		printf("assert fail %d %d!=%d\n",line,a,b);
	}
}
int main()
{
	asserteq(read(open("evil.coff"),(char*)37893872,1024),-1,__LINE__);
	asserteq(read(open("evil.coff"),(char*)main,1024),-1,__LINE__);
	asserteq(read(0,"",0),0,__LINE__);
	asserteq(write(0,(char*)37893872,1024),-1,__LINE__);
	asserteq(write(0,"",0),0,__LINE__);
	asserteq(open(0),-1,__LINE__);
	asserteq(open(""),-1,__LINE__);
	asserteq(open((char*)main),-1,__LINE__);
	asserteq(unlink(0),-1,__LINE__);
	asserteq(unlink(""),-1,__LINE__);
	asserteq(unlink((char*)main),-1,__LINE__);
	((char*)(main))[0]=1;
	return 0;
}

