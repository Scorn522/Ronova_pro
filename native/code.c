/* VM method versions and real frame locations, independent of class/module names as ownership. */
typedef struct CodeBootstrap {unsigned handle,count;unsigned *arguments;int loaded;} CodeBootstrap;
typedef struct CodePool {const unsigned char **entries;uint32_t *sizes;unsigned count,self;CodeBootstrap *bootstraps;unsigned bootstrapCount;uintptr_t vm,slots,tags,operands,holder,holderName,resolved;unsigned operandLength,resolvedCount;unsigned char *scanTags;uintptr_t *scanSlots;unsigned *utfBuckets;size_t utfBucketCount;int utfIndexed,allocated,hidden,vmReady;} CodePool;
typedef struct CodeHandler {unsigned start,end,target,type;} CodeHandler;
typedef struct CodeMethod {char *key;size_t nameLength;const unsigned char *bytes;uint32_t length;unsigned *offsets;CodeHandler *handlers;unsigned handlerCount;OwnerLink **owners;unsigned instructions,access;int sourced;struct CodeMethod *next;} CodeMethod;
typedef struct CodeVMMethod {uintptr_t method,constantMethod,constants;CodeHandler *handlers;unsigned handlerCount,codeLength;} CodeVMMethod;
typedef struct CodeImage {jweak loader,emitted,execution;int bootstrap,hidden,sourced;char *name;unsigned char *bytes;uint32_t length;uint64_t hash;CodePool pool;CodeMethod *methods;CodeMethod **methodIndex;unsigned methodCount;struct CodeImage *next,*hashNext;} CodeImage;
typedef struct CodeClass {jweak actual;CodeImage *image;struct CodeClass *next;} CodeClass;
typedef struct CodeCursor {const unsigned char *at,*end;int valid;} CodeCursor;
static SRWLOCK code_records=SRWLOCK_INIT;
static CodeImage *code_images;
static CodeImage **code_image_buckets;
static size_t code_image_bucket_count,code_image_count;
static CodeClass *code_classes;
static volatile LONG code_available,code_pop_available,code_pop_failures,code_sourced;
static jmethodID code_caller_gate,code_prepared_callback,code_pop_callback,code_root_gate;
static jobject code_control_registry;
static jfieldID code_control_backing;
static int code_class_matches(JNIEnv*,CodeImage*,jclass,jobject,const char*);
static int code_frame_sources(JNIEnv*,jmethodID,jlocation,OwnerLink**,int*);
static int code_trace(jthread,jvmtiFrameInfo**,jint*);
static int code_vm_entry(CodePool*,unsigned);
static int code_vm_self(CodePool*,unsigned);
static int code_vm_bootstrap(CodePool*,unsigned);
static int code_vm_snapshot(JNIEnv*,jmethodID,CodeVMMethod*,CodePool*,const char*,const char*);
static int code_vm_unchanged(jmethodID,CodeVMMethod*);
static uint32_t code_u(CodeCursor *cursor,unsigned size){
    if(!cursor->valid||size>(size_t)(cursor->end-cursor->at)){cursor->valid=0;return 0;}uint32_t value=0;for(unsigned i=0;i<size;i++)value=(value<<8)|*cursor->at++;return value;
}
static int code_skip(CodeCursor *cursor,uint32_t size){if(!cursor->valid||size>(size_t)(cursor->end-cursor->at)){cursor->valid=0;return 0;}cursor->at+=size;return 1;}
static uint32_t code_be(const unsigned char *at,unsigned size){uint32_t value=0;for(unsigned i=0;i<size;i++)value=(value<<8)|at[i];return value;}
static uint64_t code_image_hash(const unsigned char *bytes,size_t length){
    uint64_t hash=UINT64_C(14695981039346656037);
    for(size_t i=0;i<length;i++){hash^=bytes[i];hash*=UINT64_C(1099511628211);}return hash;
}
/* Only immutable registered bytes are indexed. Call with code_records exclusive;
 * selection still checks the complete bytes and the actual class/loader binding. */
static void code_image_publish(CodeImage *image){
    if(!code_image_bucket_count||code_image_count>=code_image_bucket_count/2){
        size_t count=code_image_bucket_count?code_image_bucket_count*2:64;
        CodeImage **buckets=count>code_image_bucket_count&&count<=SIZE_MAX/sizeof(*buckets)
                ?(CodeImage**)calloc(count,sizeof(*buckets)):NULL;
        if(buckets){
            for(CodeImage *entry=code_images;entry;entry=entry->next){size_t at=(size_t)entry->hash&(count-1);entry->hashNext=buckets[at];buckets[at]=entry;}
            // Rebuilding from the newest-first list reverses each bucket once.
            // Restore that order so identical registrations select as before.
            for(size_t i=0;i<count;i++){
                CodeImage *entry=buckets[i],*head=NULL;
                while(entry){CodeImage *next=entry->hashNext;entry->hashNext=head;head=entry;entry=next;}buckets[i]=head;
            }
            free(code_image_buckets);code_image_buckets=buckets;code_image_bucket_count=count;
        }
    }
    image->next=code_images;code_images=image;code_image_count++;
    if(code_image_bucket_count){size_t at=(size_t)image->hash&(code_image_bucket_count-1);image->hashNext=code_image_buckets[at];code_image_buckets[at]=image;}
}
static int code_pool(CodeCursor *cursor,unsigned count,CodePool *pool){
    if(count<1||count>65535)return 0;pool->entries=(const unsigned char**)calloc(count,sizeof(*pool->entries));pool->sizes=(uint32_t*)calloc(count,sizeof(*pool->sizes));pool->count=count;if(!pool->entries||!pool->sizes)return 0;
    for(unsigned i=1;i<count;i++){
        const unsigned char *entry=cursor->at;unsigned tag=code_u(cursor,1);uint32_t size;
        switch(tag){case 1:size=code_u(cursor,2);break;case 3:case 4:case 9:case 10:case 11:case 12:case 17:case 18:size=4;break;case 5:case 6:size=8;break;case 7:case 8:case 16:case 19:case 20:size=2;break;case 15:size=3;break;default:return 0;}
        if(!code_skip(cursor,size))return 0;pool->entries[i]=entry;pool->sizes[i]=(uint32_t)(cursor->at-entry);if(tag==5||tag==6){if(++i>=count)return 0;}
    }
    return cursor->valid;
}
static char *code_utf(CodePool *pool,unsigned index){
    if(index>=pool->count||!pool->entries[index]||pool->entries[index][0]!=1)return NULL;const unsigned char *entry=pool->entries[index];unsigned size=code_be(entry+1,2);char *value=(char*)calloc(size+1,1);if(value)memcpy(value,entry+3,size);return value;
}
typedef struct CodePair {unsigned first,second;} CodePair;
typedef struct CodeComparison {CodePool *first,*second;CodePair *pairs;size_t count,capacity,*buckets,bucketCount;} CodeComparison;
static size_t code_pair_hash(unsigned first,unsigned second){
    uint64_t value=((uint64_t)first<<32)|second;value^=value>>33;value*=UINT64_C(0xff51afd7ed558ccd);value^=value>>33;return (size_t)value;
}
static int code_compare_pair(CodeComparison *comparison,unsigned a,unsigned b){
    if(a>=comparison->first->count||b>=comparison->second->count)return 0;
    size_t bucket=0;
    if(comparison->bucketCount){
        bucket=code_pair_hash(a,b)&(comparison->bucketCount-1);
        while(comparison->buckets[bucket]){CodePair *pair=&comparison->pairs[comparison->buckets[bucket]-1];if(pair->first==a&&pair->second==b)return 1;bucket=(bucket+1)&(comparison->bucketCount-1);}
    }
    if(!comparison->bucketCount||comparison->count>=comparison->bucketCount/2){
        size_t count=comparison->bucketCount?comparison->bucketCount*2:32;if(count<comparison->bucketCount||count>SIZE_MAX/sizeof(size_t))return 0;
        size_t *buckets=(size_t*)calloc(count,sizeof(*buckets));if(!buckets)return 0;
        for(size_t i=0;i<comparison->count;i++){CodePair *pair=&comparison->pairs[i];size_t at=code_pair_hash(pair->first,pair->second)&(count-1);while(buckets[at])at=(at+1)&(count-1);buckets[at]=i+1;}
        free(comparison->buckets);comparison->buckets=buckets;comparison->bucketCount=count;
        bucket=code_pair_hash(a,b)&(count-1);while(buckets[bucket])bucket=(bucket+1)&(count-1);
    }
    if(comparison->count==comparison->capacity){
        size_t capacity=comparison->capacity?comparison->capacity*2:16;if(capacity<comparison->capacity||capacity>SIZE_MAX/sizeof(CodePair))return 0;
        CodePair *pairs=(CodePair*)realloc(comparison->pairs,capacity*sizeof(*pairs));if(!pairs)return 0;comparison->pairs=pairs;comparison->capacity=capacity;
    }
    comparison->pairs[comparison->count++]=(CodePair){a,b};comparison->buckets[bucket]=comparison->count;return 1;
}
static int code_constant(CodeComparison *comparison,unsigned a,unsigned b){
    CodePool *first=comparison->first,*second=comparison->second;
    // Non-hidden roots read only this comparison's immutable image and lazy
    // pool snapshot. Their completed pairs stay valid for the next root here.
    // Hidden self references read live VM identity and still start afresh.
    if(first->hidden){comparison->count=0;if(comparison->buckets)memset(comparison->buckets,0,comparison->bucketCount*sizeof(*comparison->buckets));}
    size_t start=comparison->count;
    int equal=code_compare_pair(comparison,a,b);
    for(size_t at=start;equal&&at<comparison->count;at++){
        CodePair pair=comparison->pairs[at];
        if(!code_vm_entry(first,pair.first)||!code_vm_entry(second,pair.second)){equal=0;break;}
        const unsigned char *x=first->entries[pair.first],*y=second->entries[pair.second];if(!x||!y||x[0]!=y[0]){equal=0;break;}
        switch(x[0]){
            case 7:equal=first->hidden&&pair.first==first->self?code_vm_self(second,pair.second)
                    :code_compare_pair(comparison,code_be(x+1,2),code_be(y+1,2));break;
            case 8:case 16:case 19:case 20:equal=code_compare_pair(comparison,code_be(x+1,2),code_be(y+1,2));break;
            case 9:case 10:case 11:case 12:equal=code_compare_pair(comparison,code_be(x+1,2),code_be(y+1,2))&&code_compare_pair(comparison,code_be(x+3,2),code_be(y+3,2));break;
            case 15:equal=x[1]==y[1]&&code_compare_pair(comparison,code_be(x+2,2),code_be(y+2,2));break;
            case 17:case 18:{
                unsigned left=code_be(x+1,2),right=code_be(y+1,2);
                if(left>=first->bootstrapCount||right>=second->bootstrapCount||!code_vm_bootstrap(first,left)||!code_vm_bootstrap(second,right)){equal=0;break;}
                CodeBootstrap *xb=&first->bootstraps[left],*yb=&second->bootstraps[right];
                equal=xb->count==yb->count&&code_compare_pair(comparison,code_be(x+3,2),code_be(y+3,2))&&code_compare_pair(comparison,xb->handle,yb->handle);
                for(unsigned i=0;equal&&i<xb->count;i++)equal=code_compare_pair(comparison,xb->arguments[i],yb->arguments[i]);break;
            }
            default:equal=first->sizes[pair.first]==second->sizes[pair.second]&&!memcmp(x,y,first->sizes[pair.first]);break;
        }
    }
    if(!equal){comparison->count=0;if(comparison->buckets)memset(comparison->buckets,0,comparison->bucketCount*sizeof(*comparison->buckets));}return equal;
}
static unsigned code_size(const unsigned char *bytes,unsigned length,unsigned offset){
    if(offset>=length)return 0;unsigned op=bytes[offset],size=1;
    if(op==170||op==171){unsigned base=(offset+4)&~3u;if(base+8>length)return 0;
        if(op==170){if(base+12>length)return 0;int32_t low=(int32_t)code_be(bytes+base+4,4),high=(int32_t)code_be(bytes+base+8,4);int64_t count=(int64_t)high-low+1;if(count<0||count>(length-base-12)/4)return 0;size=base-offset+12+(unsigned)count*4;}
        else {int32_t count=(int32_t)code_be(bytes+base+4,4);if(count<0||(unsigned)count>(length-base-8)/8)return 0;size=base-offset+8+(unsigned)count*8;}
    }else if(op==196){if(offset+2>length)return 0;size=bytes[offset+1]==132?6:4;}
    else if(op==16||op==18||(op>=21&&op<=25)||(op>=54&&op<=58)||op==169||op==188)size=2;
    else if(op==17||op==19||op==20||op==132||(op>=153&&op<=168)||(op>=178&&op<=184)||op==187||op==189||op==192||op==193||op==198||op==199)size=3;
    else if(op==197)size=4;else if(op==185||op==186||op==200||op==201)size=5;
    return size<=length-offset?size:0;
}
static unsigned *code_offsets(const unsigned char *bytes,unsigned length,unsigned *count){
    unsigned *offsets=(unsigned*)calloc((size_t)length+1,sizeof(*offsets));if(!offsets)return NULL;unsigned offset=0,n=0;
    while(offset<length){offsets[n++]=offset;unsigned size=code_size(bytes,length,offset);if(!size){free(offsets);return NULL;}offset+=size;}offsets[n]=length;*count=n;return offsets;
}
static int code_index(const unsigned *offsets,unsigned count,int64_t offset){
    unsigned low=0,high=count+1;while(low<high){unsigned middle=low+(high-low)/2;if(offsets[middle]<(uint64_t)offset)low=middle+1;else high=middle;}
    return offset>=0&&low<=count&&offsets[low]==(uint64_t)offset?(int)low:-1;
}
static int code_target(const unsigned char *x,unsigned xo,const unsigned char *y,unsigned yo,unsigned xdelta,unsigned ydelta,unsigned width,const unsigned *xoffsets,const unsigned *yoffsets,unsigned count){
    int64_t a=(int64_t)xo+(width==2?(int16_t)code_be(x+xo+xdelta,2):(int32_t)code_be(x+xo+xdelta,4));
    int64_t b=(int64_t)yo+(width==2?(int16_t)code_be(y+yo+ydelta,2):(int32_t)code_be(y+yo+ydelta,4));
    int first=code_index(xoffsets,count,a),second=code_index(yoffsets,count,b);return first>=0&&first==second;
}
static int code_equivalent(CodeImage *image,CodeMethod *method,const unsigned char *actual,unsigned length,CodePool *pool,CodeVMMethod *version,jlocation location,unsigned *ordinal){
    CodeComparison comparison={&image->pool,pool,NULL,0,0,NULL,0};
    unsigned aCount=method->instructions,bCount=0;const unsigned *a=method->offsets;unsigned *b=code_offsets(actual,length,&bCount);int equal=a&&b&&aCount==bCount;
    for(unsigned i=0;equal&&i<aCount;i++){
        unsigned x=a[i],y=b[i],xo=method->bytes[x],yo=actual[y],xs=a[i+1]-x,ys=b[i+1]-y;unsigned xn=xo==19?18:xo,yn=yo==19?18:yo;
        if(xn!=yn){equal=0;break;}
        if((xo>=153&&xo<=168)||xo==198||xo==199||xo==200||xo==201){equal=code_target(method->bytes,x,actual,y,1,1,xo>=200?4:2,a,b,aCount);continue;}
        if(xo==170||xo==171){
            unsigned xb=(x+4)&~3u,yb=(y+4)&~3u;equal=code_target(method->bytes,x,actual,y,xb-x,yb-y,4,a,b,aCount);
            unsigned header=xo==170?12:8;if(equal&&memcmp(method->bytes+xb+4,actual+yb+4,header-4))equal=0;
            unsigned entries=xo==170?(xs-(xb-x)-12)/4:(xs-(xb-x)-8)/8;
            if(equal&&(ys-(yb-y)-header)!=(xs-(xb-x)-header))equal=0;
            for(unsigned j=0;equal&&j<entries;j++){unsigned step=xo==170?4:8,delta=header+j*step;if(xo==171&&memcmp(method->bytes+xb+delta,actual+yb+delta,4))equal=0;equal=equal&&code_target(method->bytes,x,actual,y,xb-x+delta+(xo==171?4:0),yb-y+delta+(xo==171?4:0),4,a,b,aCount);}continue;
        }
        int constant=xo==18||xo==19||xo==20||(xo>=178&&xo<=187)||xo==189||xo==192||xo==193||xo==197;
        if(constant){
            unsigned xi=code_be(method->bytes+x+1,xo==18?1:2),yi=code_be(actual+y+1,yo==18?1:2);equal=code_constant(&comparison,xi,yi);
            unsigned xhead=xo==18?2:3,yhead=yo==18?2:3;if(xs-xhead!=ys-yhead||memcmp(method->bytes+x+xhead,actual+y+yhead,xs-xhead))equal=0;
        }else if(xs!=ys||memcmp(method->bytes+x,actual+y,xs))equal=0;
    }
    equal=equal&&version&&version->codeLength==length&&version->handlerCount==method->handlerCount;
    for(unsigned i=0;equal&&i<method->handlerCount;i++){
        CodeHandler *expected=&method->handlers[i],*observed=&version->handlers[i];
        int start=code_index(a,aCount,expected->start),end=code_index(a,aCount,expected->end),target=code_index(a,aCount,expected->target);
        equal=start>=0&&end>start&&target>=0&&(unsigned)target<aCount
                &&start==code_index(b,bCount,observed->start)&&end==code_index(b,bCount,observed->end)&&target==code_index(b,bCount,observed->target);
        if(equal)equal=expected->type==0?observed->type==0:observed->type!=0&&code_constant(&comparison,expected->type,observed->type);
    }
    if(location>=0){int at=equal?code_index(b,bCount,location):-1;if(at<0||(unsigned)at>=bCount)equal=0;else *ordinal=(unsigned)at;}
    free(comparison.pairs);free(comparison.buckets);free(b);return equal;
}
static void code_pool_free(CodePool *pool){
    if(pool->allocated&&pool->entries)for(unsigned i=0;i<pool->count;i++)free((void*)pool->entries[i]);
    if(pool->bootstraps)for(unsigned i=0;i<pool->bootstrapCount;i++)free(pool->bootstraps[i].arguments);
    free(pool->bootstraps);free(pool->entries);free(pool->sizes);free(pool->scanTags);free(pool->scanSlots);free(pool->utfBuckets);memset(pool,0,sizeof(*pool));
}
#include "vm_metadata.c"
static void code_links_free(OwnerLink *links){while(links){OwnerLink *next=links->next;free(links);links=next;}}
static void code_image_free(JNIEnv *env,CodeImage *image){
    if(image->loader)native_original.DeleteWeakGlobalRef(env,image->loader);if(image->emitted)native_original.DeleteWeakGlobalRef(env,image->emitted);if(image->execution)native_original.DeleteWeakGlobalRef(env,image->execution);CodeMethod *method=image->methods;
    while(method){CodeMethod *next=method->next;if(method->owners)for(unsigned i=0;i<method->instructions;i++)code_links_free(method->owners[i]);free(method->owners);free(method->offsets);free(method->handlers);free(method->key);free(method);method=next;}
    code_pool_free(&image->pool);free(image->methodIndex);free(image->bytes);free(image->name);free(image);
}
static int code_attributes(CodeCursor *cursor,CodePool *pool,CodeMethod *method){
    unsigned count=code_u(cursor,2);for(unsigned i=0;i<count&&cursor->valid;i++){
        char *name=code_utf(pool,code_u(cursor,2));uint32_t size=code_u(cursor,4);if(!name||size>(size_t)(cursor->end-cursor->at)){free(name);return 0;}
        if(method&&strcmp(name,"Code")==0){
            if(method->bytes){free(name);return 0;}
            CodeCursor body={cursor->at,cursor->at+size,1};code_u(&body,2);code_u(&body,2);method->length=code_u(&body,4);method->bytes=body.at;
            if(!code_skip(&body,method->length)){free(name);return 0;}
            method->handlerCount=code_u(&body,2);
            if(method->handlerCount){method->handlers=(CodeHandler*)calloc(method->handlerCount,sizeof(*method->handlers));if(!method->handlers){free(name);return 0;}}
            for(unsigned j=0;j<method->handlerCount&&body.valid;j++){
                CodeHandler *handler=&method->handlers[j];handler->start=code_u(&body,2);handler->end=code_u(&body,2);handler->target=code_u(&body,2);handler->type=code_u(&body,2);
                if(handler->start>=handler->end||handler->end>method->length||handler->target>=method->length)body.valid=0;
            }
            if(!body.valid||!code_attributes(&body,pool,NULL)||body.at!=body.end){free(name);return 0;}
        }
        free(name);if(!code_skip(cursor,size))return 0;
    }return cursor->valid;
}
static int code_class_attributes(CodeCursor *cursor,CodePool *pool){
    unsigned count=code_u(cursor,2);
    for(unsigned i=0;i<count&&cursor->valid;i++){
        char *name=code_utf(pool,code_u(cursor,2));uint32_t size=code_u(cursor,4);
        if(!name||size>(size_t)(cursor->end-cursor->at)){free(name);return 0;}
        if(!strcmp(name,"BootstrapMethods")){
            if(pool->bootstraps){free(name);return 0;}
            CodeCursor body={cursor->at,cursor->at+size,1};pool->bootstrapCount=code_u(&body,2);
            pool->bootstraps=(CodeBootstrap*)calloc((size_t)pool->bootstrapCount+1,sizeof(*pool->bootstraps));if(!pool->bootstraps){free(name);return 0;}
            for(unsigned j=0;j<pool->bootstrapCount&&body.valid;j++){
                CodeBootstrap *bootstrap=&pool->bootstraps[j];bootstrap->handle=code_u(&body,2);bootstrap->count=code_u(&body,2);
                if(bootstrap->count){bootstrap->arguments=(unsigned*)calloc(bootstrap->count,sizeof(unsigned));if(!bootstrap->arguments){free(name);return 0;}}
                for(unsigned k=0;k<bootstrap->count&&body.valid;k++)bootstrap->arguments[k]=code_u(&body,2);bootstrap->loaded=body.valid;
            }
            if(!body.valid||body.at!=body.end){free(name);return 0;}
        }
        free(name);if(!code_skip(cursor,size))return 0;
    }
    return cursor->valid&&cursor->at==cursor->end;
}
static int code_members(CodeCursor *cursor,CodeImage *image,int methods){
    unsigned count=code_u(cursor,2);for(unsigned i=0;i<count&&cursor->valid;i++){
        unsigned access=code_u(cursor,2);char *name=code_utf(&image->pool,code_u(cursor,2)),*descriptor=code_utf(&image->pool,code_u(cursor,2));if(!name||!descriptor){free(name);free(descriptor);return 0;}
        CodeMethod *method=NULL;if(methods){method=(CodeMethod*)calloc(1,sizeof(*method));if(!method){free(name);free(descriptor);return 0;}method->nameLength=strlen(name);method->key=(char*)malloc(method->nameLength+strlen(descriptor)+1);if(!method->key){free(name);free(descriptor);free(method);return 0;}strcpy(method->key,name);strcat(method->key,descriptor);method->next=image->methods;image->methods=method;image->methodCount++;}
        free(name);free(descriptor);if(!code_attributes(cursor,&image->pool,method))return 0;
        if(method){method->access=access;method->offsets=code_offsets(method->bytes,method->length,&method->instructions);if(!method->offsets)return 0;}
    }return cursor->valid;
}
static int code_method_order(CodeMethod *entry,const char *name,size_t length,const char *descriptor){
    size_t prefix=entry->nameLength<length?entry->nameLength:length;int order=memcmp(entry->key,name,prefix);
    if(order)return order;if(entry->nameLength!=length)return entry->nameLength<length?-1:1;
    return strcmp(entry->key+entry->nameLength,descriptor);
}
static int code_method_compare(const void *first,const void *second){
    CodeMethod *a=*(CodeMethod*const*)first,*b=*(CodeMethod*const*)second;
    return code_method_order(a,b->key,b->nameLength,b->key+b->nameLength);
}
static int code_methods_index(CodeImage *image){
    if(!image->methodCount)return 1;
    image->methodIndex=(CodeMethod**)calloc(image->methodCount,sizeof(*image->methodIndex));if(!image->methodIndex)return 0;
    unsigned at=0;for(CodeMethod *entry=image->methods;entry;entry=entry->next)image->methodIndex[at++]=entry;
    qsort(image->methodIndex,image->methodCount,sizeof(*image->methodIndex),code_method_compare);
    for(unsigned i=1;i<image->methodCount;i++)if(!code_method_compare(&image->methodIndex[i-1],&image->methodIndex[i]))return 0;
    return 1;
}
static CodeMethod *code_method(CodeImage *image,const char *name,const char *descriptor){
    if(!name||!descriptor)return NULL;size_t length=strlen(name);unsigned low=0,high=image->methodCount;
    while(low<high){unsigned middle=low+(high-low)/2;CodeMethod *entry=image->methodIndex[middle];int order=code_method_order(entry,name,length,descriptor);
        if(!order)return entry;if(order<0)low=middle+1;else high=middle;}
    return NULL;
}
static int code_initialize(void){
    jvmtiCapabilities capabilities;memset(&capabilities,0,sizeof(capabilities));capabilities.can_get_bytecodes=1;
    int ready=(*native_ti)->AddCapabilities(native_ti,&capabilities)==JVMTI_ERROR_NONE;InterlockedExchange(&code_available,ready);
    memset(&capabilities,0,sizeof(capabilities));capabilities.can_generate_frame_pop_events=1;
    int pops=(*native_ti)->AddCapabilities(native_ti,&capabilities)==JVMTI_ERROR_NONE
            &&(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_FRAME_POP,NULL)==JVMTI_ERROR_NONE;
    InterlockedExchange(&code_pop_available,pops);return ready;
}
static int code_prepare(JNIEnv *env){
    // Heap/JNI observers can run as soon as their hooks are published, before
    // the first producer class supplies a code image.
    if(!code_caller_gate)code_caller_gate=native_original.GetStaticMethodID(env,native_controller,"codeCaller","()Z");
    if(!code_prepared_callback)code_prepared_callback=native_original.GetStaticMethodID(env,native_controller,"codePrepared","(Ljava/lang/Class;)V");
    if(!code_pop_callback)code_pop_callback=native_original.GetStaticMethodID(env,native_controller,"executionPopped","(Ljava/lang/Object;Z)Z");
    if(!code_root_gate)code_root_gate=native_original.GetStaticMethodID(env,native_controller,"executionRootCaller","()Z");
    if(!code_control_registry){
        jclass bridge=native_original.FindClass(env,"dev/ronova/pro/bootstrap/CodeSourceBridge");
        jclass map=native_original.FindClass(env,"dev/ronova/pro/bootstrap/CodeSourceBridge$ControlRegistry");jobject registry=NULL;jclass actual=NULL;
        if(bridge&&map&&native_bootstrap_class(env,bridge,"Ldev/ronova/pro/bootstrap/CodeSourceBridge;")
                &&native_bootstrap_class(env,map,"Ldev/ronova/pro/bootstrap/CodeSourceBridge$ControlRegistry;")){
            jfieldID field=native_original.GetStaticFieldID(env,bridge,"CONTROLS","Ldev/ronova/pro/bootstrap/CodeSourceBridge$ControlRegistry;");
            registry=field?native_original.GetStaticObjectField(env,bridge,field):NULL;
            actual=registry?native_original.GetObjectClass(env,registry):NULL;
            if(actual&&native_original.IsSameObject(env,actual,map)){
                code_control_backing=native_original.GetFieldID(env,map,"table","[Ldev/ronova/pro/bootstrap/CodeSourceBridge$ControlKey;");
                if(code_control_backing)code_control_registry=native_original.NewGlobalRef(env,registry);
            }
        }
        if(actual)native_original.DeleteLocalRef(env,actual);if(registry)native_original.DeleteLocalRef(env,registry);
        if(bridge)native_original.DeleteLocalRef(env,bridge);if(map)native_original.DeleteLocalRef(env,map);
    }
    return code_caller_gate&&code_prepared_callback&&code_pop_callback&&code_root_gate
            &&code_control_registry&&code_control_backing&&!native_original.ExceptionCheck(env);
}
JNIEXPORT jobject JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_controlTable0(JNIEnv *env,jclass type){
    if(!native_jni_ready||!native_original.IsSameObject(env,type,native_controller)
            ||!code_control_registry||!code_control_backing||!code_caller_gate
            ||!native_original.CallStaticBooleanMethod(env,native_controller,code_caller_gate)){
        native_refuse(env,"ACTUAL_CONTROL_TABLE_READER_REQUIRED");return NULL;
    }
    // Read only this actual control registry's current table. The original JNI
    // getter keeps the VM's reference barrier without reentering heap-source recording.
    return native_original.GetObjectField(env,code_control_registry,code_control_backing);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_codeLayout0(JNIEnv *env,jclass type,jobject loader,jstring name,jbyteArray bytes,jobjectArray keys,jobjectArray rows,jboolean hidden,jobject execution){
    (void)type;if(!native_jni_ready||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"ACTUAL_CODE_LAYOUT_AGENT_REQUIRED");return JNI_FALSE;}
    if(!code_available)return JNI_FALSE;CodeImage *image=(CodeImage*)calloc(1,sizeof(*image));if(!image)return JNI_FALSE;
    image->bootstrap=loader==NULL;image->hidden=hidden==JNI_TRUE;image->loader=loader?native_original.NewWeakGlobalRef(env,loader):NULL;const char *text=native_original.GetStringUTFChars(env,name,NULL);if(text){image->name=_strdup(text);native_original.ReleaseStringUTFChars(env,name,text);}
    image->emitted=image->hidden?native_original.NewWeakGlobalRef(env,bytes):NULL;
    image->execution=execution?native_original.NewWeakGlobalRef(env,execution):NULL;
    image->length=(uint32_t)native_original.GetArrayLength(env,bytes);image->bytes=(unsigned char*)malloc(image->length);if(image->bytes)native_original.GetByteArrayRegion(env,bytes,0,image->length,(jbyte*)image->bytes);
    int valid=image->name&&image->bytes&&(!loader||image->loader)&&(!image->hidden||image->emitted)&&(!execution||image->execution)&&!native_original.ExceptionCheck(env);CodeCursor cursor={image->bytes,image->bytes?image->bytes+image->length:NULL,valid};
    valid=valid&&code_u(&cursor,4)==0xcafebabe;code_u(&cursor,2);code_u(&cursor,2);unsigned count=code_u(&cursor,2);valid=valid&&code_pool(&cursor,count,&image->pool);code_u(&cursor,2);unsigned self=code_u(&cursor,2);code_u(&cursor,2);
    char *declared=NULL;if(valid&&self<image->pool.count&&image->pool.entries[self]&&image->pool.entries[self][0]==7)declared=code_utf(&image->pool,code_be(image->pool.entries[self]+1,2));valid=valid&&declared&&!strcmp(declared,image->name);free(declared);
    image->pool.self=self;image->pool.hidden=image->hidden;
    valid=valid&&code_skip(&cursor,code_u(&cursor,2)*2)&&code_members(&cursor,image,0)&&code_members(&cursor,image,1)&&code_class_attributes(&cursor,&image->pool)&&code_methods_index(image);
    jsize total=native_original.GetArrayLength(env,keys);valid=valid&&total==native_original.GetArrayLength(env,rows);
    AcquireSRWLockExclusive(&native_records);
    for(jsize i=0;valid&&i<total;i++){
        jstring key=(jstring)native_original.GetObjectArrayElement(env,keys,i);const char *value=key?native_original.GetStringUTFChars(env,key,NULL):NULL;CodeMethod *method=image->methods;while(method&&(!value||strcmp(method->key,value)))method=method->next;
        if(value)native_original.ReleaseStringUTFChars(env,key,value);if(key)native_original.DeleteLocalRef(env,key);jobjectArray owners=(jobjectArray)native_original.GetObjectArrayElement(env,rows,i);
        unsigned instructions=method?method->instructions:0;valid=method&&method->offsets&&owners&&instructions==(unsigned)native_original.GetArrayLength(env,owners)&&!method->owners;
        if(valid){method->owners=(OwnerLink**)calloc(instructions,sizeof(*method->owners));valid=method->owners!=NULL;}
        for(unsigned j=0;valid&&j<instructions;j++){jobjectArray source=(jobjectArray)native_original.GetObjectArrayElement(env,owners,j);valid=source!=NULL;jsize n=source?native_original.GetArrayLength(env,source):0;
            for(jsize k=0;valid&&k<n;k++){jobject module=native_original.GetObjectArrayElement(env,source,k);Owner *owner=native_owner(env,module);valid=owner&&native_owner_add(&method->owners[j],owner);if(module)native_original.DeleteLocalRef(env,module);}
            if(method->owners[j]){method->sourced=1;image->sourced=1;}
            if(source)native_original.DeleteLocalRef(env,source);
        }if(owners)native_original.DeleteLocalRef(env,owners);
    }
    ReleaseSRWLockExclusive(&native_records);valid=valid&&!native_original.ExceptionCheck(env);
    if(!valid){code_image_free(env,image);native_refuse(env,"ACTUAL_CODE_LAYOUT_UNAVAILABLE");return JNI_FALSE;}
    image->hash=code_image_hash(image->bytes,image->length);
    AcquireSRWLockExclusive(&code_records);code_image_publish(image);
    // Set before the image can become executing VM code. Published owner
    // arrays do not change; keep a positive presence even after owner GC/stop.
    if(image->sourced)InterlockedExchange(&code_sourced,1);
    ReleaseSRWLockExclusive(&code_records);return JNI_TRUE;
}
static int code_method_relevant(JNIEnv *env,jmethodID method,jobject module){
    if(!InterlockedCompareExchange(&code_sourced,0,0))return 0;
    jclass declaring=NULL;jobject loader=NULL;char *signature=NULL,*name=NULL,*descriptor=NULL;int relevant=0;
    if((*native_ti)->GetMethodDeclaringClass(native_ti,method,&declaring)==JVMTI_ERROR_NONE&&(*native_ti)->GetClassLoader(native_ti,declaring,&loader)==JVMTI_ERROR_NONE
            &&(*native_ti)->GetClassSignature(native_ti,declaring,&signature,NULL)==JVMTI_ERROR_NONE&&(*native_ti)->GetMethodName(native_ti,method,&name,&descriptor,NULL)==JVMTI_ERROR_NONE){
        // Published instruction-owner arrays are immutable. Empty images and
        // methods cannot satisfy the existing owner predicate; positive rows
        // still use actual class matching, live owner checks and VM validation.
        AcquireSRWLockShared(&code_records);for(CodeImage *image=code_images;image&&!relevant;image=image->next)if(image->sourced&&code_class_matches(env,image,declaring,loader,signature)){
            CodeMethod *entry=code_method(image,name,descriptor);if(!entry||!entry->sourced||!entry->owners)continue;
            for(unsigned i=0;i<entry->instructions&&!relevant;i++)if(native_owner_matches(env,entry->owners[i],module)&&entry->owners[i])relevant=1;
        }ReleaseSRWLockShared(&code_records);
    }
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);if(loader)native_original.DeleteLocalRef(env,loader);if(declaring)native_original.DeleteLocalRef(env,declaring);return relevant;
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_codeState0(JNIEnv *env,jclass type,jobject module){
    (void)type;if(!native_jni_ready||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"ACTUAL_CODE_STATE_AGENT_REQUIRED");return NULL;}
    jlong values[5]={code_available,0,0,code_pop_available,code_pop_failures};jint count=0;jthread *threads=NULL;
    if(code_available&&(*native_ti)->GetAllThreads(native_ti,&count,&threads)==JVMTI_ERROR_NONE){
        for(jint i=0;i<count;i++){
            jvmtiFrameInfo *frames=NULL;jint depth=0;
            if(code_trace(threads[i],&frames,&depth))for(jint j=0;j<depth;j++){
                if(frames[j].location<0||!code_method_relevant(env,frames[j].method,module))continue;OwnerLink *owners=NULL;int known=0;
                if(!code_frame_sources(env,frames[j].method,frames[j].location,&owners,&known))values[2]++;
                else if(owners&&native_owner_matches(env,owners,module))values[1]++;code_links_free(owners);
            }
            else {jint state=0;if((*native_ti)->GetThreadState(native_ti,threads[i],&state)!=JVMTI_ERROR_NONE||(state&JVMTI_THREAD_STATE_ALIVE))values[2]++;}
            free(frames);native_original.DeleteLocalRef(env,threads[i]);
        }(*native_ti)->Deallocate(native_ti,(unsigned char*)threads);
    }else if(code_available)values[2]++;
    jlongArray result=native_original.NewLongArray(env,5);if(result)native_original.SetLongArrayRegion(env,result,0,5,values);return result;
}
static int code_class_matches(JNIEnv *env,CodeImage *image,jclass actual,jobject loader,const char *signature){
    if(image->hidden){for(CodeClass *entry=code_classes;entry;entry=entry->next)if(entry->image==image&&native_original.IsSameObject(env,entry->actual,actual))return 1;return 0;}
    return signature[0]=='L'&&strlen(signature)==strlen(image->name)+2&&!memcmp(signature+1,image->name,strlen(image->name))
            &&image->bootstrap==(loader==NULL)&&(!loader||native_original.IsSameObject(env,image->loader,loader));
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_codeDefinition0(JNIEnv *env,jclass controller,jclass actual,jbyteArray expected){
    (void)controller;if(!native_jni_ready||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"ACTUAL_CODE_DEFINITION_AGENT_REQUIRED");return JNI_FALSE;}
    if(!code_available||!actual||!expected)return JNI_FALSE;
    jclass classType=native_original.FindClass(env,"java/lang/Class");jmethodID hidden=classType?native_original.GetMethodID(env,classType,"isHidden","()Z"):NULL;
    int valid=hidden&&native_original.CallBooleanMethod(env,actual,hidden)&&!native_original.ExceptionCheck(env);if(classType)native_original.DeleteLocalRef(env,classType);if(!valid)return JNI_FALSE;
    jobject loader=NULL;if((*native_ti)->GetClassLoader(native_ti,actual,&loader)!=JVMTI_ERROR_NONE)return JNI_FALSE;
    jsize length=native_original.GetArrayLength(env,expected);jbyte *bytes=native_original.GetByteArrayElements(env,expected,NULL);CodeImage *selected=NULL;
    if(bytes){AcquireSRWLockShared(&code_records);for(CodeImage *image=code_images;image;image=image->next)
        if(image->hidden&&native_original.IsSameObject(env,image->emitted,expected)&&image->bootstrap==(loader==NULL)&&(!loader||native_original.IsSameObject(env,image->loader,loader))
                &&image->length==(uint32_t)length&&!memcmp(image->bytes,bytes,length)){selected=image;break;}
        ReleaseSRWLockShared(&code_records);native_original.ReleaseByteArrayElements(env,expected,bytes,JNI_ABORT);}
    if(loader)native_original.DeleteLocalRef(env,loader);if(!selected)return JNI_FALSE;
    CodeClass *bound=(CodeClass*)calloc(1,sizeof(*bound));if(!bound)return JNI_FALSE;bound->actual=native_original.NewWeakGlobalRef(env,actual);bound->image=selected;
    if(!bound->actual){free(bound);return JNI_FALSE;}
    AcquireSRWLockExclusive(&code_records);CodeClass **at=&code_classes;
    while(*at){CodeClass *entry=*at;
        if(native_original.IsSameObject(env,entry->actual,NULL)){*at=entry->next;native_original.DeleteWeakGlobalRef(env,entry->actual);free(entry);continue;}
        if(native_original.IsSameObject(env,entry->actual,actual)){valid=entry->image==selected;ReleaseSRWLockExclusive(&code_records);native_original.DeleteWeakGlobalRef(env,bound->actual);free(bound);return valid?JNI_TRUE:JNI_FALSE;}
        at=&entry->next;
    }
    bound->next=code_classes;code_classes=bound;ReleaseSRWLockExclusive(&code_records);return JNI_TRUE;
}
/* An authenticated controller query builds only its fresh result array. Application
 * JNI writes keep using the installed mutation boundaries. No class is loaded here. */
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_bootstrapLookupClasses0(JNIEnv *env,jclass controller){
    (void)controller;
    if(!native_jni_ready||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"ACTUAL_BOOTSTRAP_CLASS_QUERY_REQUIRED");return NULL;
    }
    // This name lookup needs only the bootstrap loader's initiated classes.
    // Hidden definitions continue through the unchanged full inventory query.
    jclass *classes=NULL,element=NULL;jint count=0;jobjectArray result=NULL;int ready=0;
    if((*native_ti)->GetClassLoaderClasses(native_ti,NULL,&count,&classes)!=JVMTI_ERROR_NONE||count<0)goto done;
    element=native_original.FindClass(env,"java/lang/Class");if(!element)goto done;
    result=native_original.NewObjectArray(env,count,element,NULL);if(!result)goto done;
    for(jint i=0;i<count;i++){
        native_original.SetObjectArrayElement(env,result,i,classes[i]);if(native_original.ExceptionCheck(env))goto done;
    }
    ready=1;
done:
    if(classes){for(jint i=0;i<count;i++)native_original.DeleteLocalRef(env,classes[i]);(*native_ti)->Deallocate(native_ti,(unsigned char*)classes);}
    if(element)native_original.DeleteLocalRef(env,element);
    if(!ready){if(result)native_original.DeleteLocalRef(env,result);native_refuse(env,"ACTUAL_BOOTSTRAP_CLASS_QUERY_UNAVAILABLE");return NULL;}
    return result;
}
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_loadedClasses0(JNIEnv *env,jclass controller,jboolean bootstrapOnly){
    (void)controller;
    if(!native_jni_ready||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"ACTUAL_LOADED_CLASS_QUERY_REQUIRED");return NULL;
    }
    jclass *classes=NULL,element=NULL;jint count=0,selectedCount=0;unsigned char *selected=NULL;jobjectArray result=NULL;int ready=0;
    if((*native_ti)->GetLoadedClasses(native_ti,&count,&classes)!=JVMTI_ERROR_NONE||count<0)goto done;
    if(bootstrapOnly){
        selected=(unsigned char*)calloc(count?count:1,1);if(!selected)goto done;
        for(jint i=0;i<count;i++){
            jobject loader=NULL;jvmtiError status=(*native_ti)->GetClassLoader(native_ti,classes[i],&loader);
            if(status!=JVMTI_ERROR_NONE){if(loader)native_original.DeleteLocalRef(env,loader);goto done;}
            if(!loader){selected[i]=1;selectedCount++;}else native_original.DeleteLocalRef(env,loader);
        }
    }else selectedCount=count;
    element=native_original.FindClass(env,"java/lang/Class");if(!element)goto done;
    result=native_original.NewObjectArray(env,selectedCount,element,NULL);if(!result)goto done;
    for(jint i=0,at=0;i<count;i++)if(!bootstrapOnly||selected[i]){
        native_original.SetObjectArrayElement(env,result,at++,classes[i]);if(native_original.ExceptionCheck(env))goto done;
    }
    ready=1;
done:
    if(classes){for(jint i=0;i<count;i++)native_original.DeleteLocalRef(env,classes[i]);(*native_ti)->Deallocate(native_ti,(unsigned char*)classes);}
    if(element)native_original.DeleteLocalRef(env,element);free(selected);
    if(!ready){if(result)native_original.DeleteLocalRef(env,result);native_refuse(env,"ACTUAL_LOADED_CLASS_QUERY_UNAVAILABLE");return NULL;}
    return result;
}
typedef struct CodeDeclaration {char *name,*descriptor;jint access;CodeMethod *imageMethod;int matched;} CodeDeclaration;
typedef struct CodeDeclarations {jmethodID *methods;CodeDeclaration *entries;jint count;} CodeDeclarations;
static void code_declarations_free(CodeDeclarations *query){
    if(query->entries)for(jint i=0;i<query->count;i++){
        if(query->entries[i].name)(*native_ti)->Deallocate(native_ti,(unsigned char*)query->entries[i].name);
        if(query->entries[i].descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)query->entries[i].descriptor);
    }
    free(query->entries);if(query->methods)(*native_ti)->Deallocate(native_ti,(unsigned char*)query->methods);memset(query,0,sizeof(*query));
}
/* The snapshot belongs only to this query. Validate the complete declaration
 * shape before spending any work on bodies that a shape mismatch discards. */
static int code_declarations(jclass type,CodeImage *image,CodeDeclarations *query){
    if((*native_ti)->GetClassMethods(native_ti,type,&query->count,&query->methods)!=JVMTI_ERROR_NONE||query->count<0
            ||(size_t)query->count>SIZE_MAX/sizeof(*query->entries)||(!image&&query->count>INT32_MAX/3)
            ||(image&&image->methodCount!=(unsigned)query->count))return 0;
    query->entries=(CodeDeclaration*)calloc(query->count?query->count:1,sizeof(*query->entries));if(!query->entries)return 0;
    for(jint i=0;i<query->count;i++){
        CodeDeclaration *entry=&query->entries[i];
        if((*native_ti)->GetMethodName(native_ti,query->methods[i],&entry->name,&entry->descriptor,NULL)!=JVMTI_ERROR_NONE
                ||(*native_ti)->GetMethodModifiers(native_ti,query->methods[i],&entry->access)!=JVMTI_ERROR_NONE)return 0;
        if(image){entry->imageMethod=code_method(image,entry->name,entry->descriptor);
            if(!entry->imageMethod||entry->imageMethod->access!=(unsigned)entry->access)return 0;}
    }
    return 1;
}
/* JVMTI headers distinguish an absent declaration from an unavailable body without resolving parameter classes. */
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_codeDeclarations0(JNIEnv *env,jclass controller,jclass type){
    (void)controller;if(!native_jni_ready||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"ACTUAL_CODE_DECLARATIONS_AGENT_REQUIRED");return NULL;}
    if(!native_ti||!type)return NULL;
    CodeDeclarations query={0};jclass string=NULL;jobjectArray values=NULL,result=NULL;jstring modifiers=NULL;jint lastAccess=0;
    if(!code_declarations(type,NULL,&query)||query.count>INT32_MAX/3)goto done;
    string=native_original.FindClass(env,"java/lang/String");if(!string)goto done;
    values=native_original.NewObjectArray(env,query.count*3,string,NULL);if(!values)goto done;
    for(jint i=0;i<query.count;i++){
        CodeDeclaration *entry=&query.entries[i];
        if(!modifiers||lastAccess!=entry->access){
            char flags[9];const char *hex="0123456789abcdef";
            for(unsigned digit=0;digit<8;digit++)flags[digit]=hex[((uint32_t)entry->access>>(28-digit*4))&15];flags[8]=0;
            if(modifiers)native_original.DeleteLocalRef(env,modifiers);
            modifiers=native_original.NewStringUTF(env,flags);lastAccess=entry->access;if(!modifiers)goto done;
        }
        jstring method=native_original.NewStringUTF(env,entry->name),signature=method?native_original.NewStringUTF(env,entry->descriptor):NULL;
        int ready=signature!=NULL;
        if(ready){
            native_original.SetObjectArrayElement(env,values,i*3,method);
            native_original.SetObjectArrayElement(env,values,i*3+1,signature);
            native_original.SetObjectArrayElement(env,values,i*3+2,modifiers);
        }
        if(method)native_original.DeleteLocalRef(env,method);if(signature)native_original.DeleteLocalRef(env,signature);
        if(!ready||native_original.ExceptionCheck(env))goto done;
    }
    result=values;values=NULL;
done:
    if(values)native_original.DeleteLocalRef(env,values);if(string)native_original.DeleteLocalRef(env,string);if(modifiers)native_original.DeleteLocalRef(env,modifiers);
    code_declarations_free(&query);return result;
}
/* The returned selectors belong to this actual class and this emitted bytecode version. */
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_codeVersion0(JNIEnv *env,jclass controller,jclass type,jbyteArray expected){
    (void)controller;if(!native_jni_ready||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"ACTUAL_CODE_VERSION_AGENT_REQUIRED");return NULL;}
    if(!code_available||!type||!expected)return NULL;
    jobject loader=NULL;char *signature=NULL;CodeDeclarations query={0};CodeVMBuffer scratch={0};CodePool pool={0};CodeImage *selected=NULL;
    jbyte *bytes=NULL;jobjectArray result=NULL;jclass string=NULL;int ready=(*native_ti)->GetClassLoader(native_ti,type,&loader)==JVMTI_ERROR_NONE
            &&(*native_ti)->GetClassSignature(native_ti,type,&signature,NULL)==JVMTI_ERROR_NONE;
    if(!ready)goto done;
    jsize length=native_original.GetArrayLength(env,expected);bytes=native_original.GetByteArrayElements(env,expected,NULL);if(!bytes)goto done;
    uint64_t hash=code_image_hash((const unsigned char*)bytes,(size_t)length);
    AcquireSRWLockShared(&code_records);
    for(CodeImage *image=code_image_bucket_count?code_image_buckets[(size_t)hash&(code_image_bucket_count-1)]:code_images;image;image=code_image_bucket_count?image->hashNext:image->next)
        if(image->hash==hash&&image->length==(uint32_t)length&&!memcmp(image->bytes,bytes,length)&&code_class_matches(env,image,type,loader,signature)){selected=image;break;}
    ReleaseSRWLockShared(&code_records);native_original.ReleaseByteArrayElements(env,expected,bytes,JNI_ABORT);bytes=NULL;
    if(!selected||!code_declarations(type,selected,&query))goto done;
    string=native_original.FindClass(env,"java/lang/String");if(!string)goto done;
    jsize matched=0;
    for(jint i=0;i<query.count&&!native_original.ExceptionCheck(env);i++){
        CodeDeclaration *declaration=&query.entries[i];CodeMethod *entry=declaration->imageMethod;
        unsigned char *actual=NULL;jint size=0;CodeVMMethod version={0};
        ready=(declaration->access&(0x0100|0x0400))==0;
        if(ready)ready=code_vm_snapshot_query(env,query.methods[i],&version,&pool,declaration->name,declaration->descriptor,&scratch);
        if(ready)ready=(*native_ti)->GetBytecodes(native_ti,query.methods[i],&size,&actual)==JVMTI_ERROR_NONE;
        unsigned ordinal=0;if(ready)ready=code_equivalent(selected,entry,actual,size,&pool,&version,-1,&ordinal)&&code_vm_unchanged(query.methods[i],&version);
        if(ready){declaration->matched=1;matched++;}
        free(version.handlers);if(actual)(*native_ti)->Deallocate(native_ti,actual);
    }
    if(!native_original.ExceptionCheck(env)){
        result=native_original.NewObjectArray(env,matched,string,NULL);
        for(jint i=0,at=0;result&&i<query.count&&!native_original.ExceptionCheck(env);i++)if(query.entries[i].matched){
            jstring key=native_original.NewStringUTF(env,query.entries[i].imageMethod->key);
            if(key){native_original.SetObjectArrayElement(env,result,at++,key);native_original.DeleteLocalRef(env,key);}
            else {native_original.DeleteLocalRef(env,result);result=NULL;}
        }
    }
done:
    if(bytes)native_original.ReleaseByteArrayElements(env,expected,bytes,JNI_ABORT);code_pool_free(&pool);code_vm_buffer_free(&scratch);code_declarations_free(&query);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    if(loader)native_original.DeleteLocalRef(env,loader);if(string)native_original.DeleteLocalRef(env,string);return result;
}
static void JNICALL code_class_prepared(jvmtiEnv *ti,JNIEnv *env,jthread thread,jclass type){
    (void)thread;if(!native_jni_ready||!code_prepared_callback)return;jobject loader=NULL;char *signature=NULL;int candidate=0;
    if((*ti)->GetClassLoader(ti,type,&loader)==JVMTI_ERROR_NONE&&(*ti)->GetClassSignature(ti,type,&signature,NULL)==JVMTI_ERROR_NONE){
        AcquireSRWLockShared(&code_records);for(CodeImage *image=code_images;image;image=image->next)if(code_class_matches(env,image,type,loader,signature)){candidate=1;break;}ReleaseSRWLockShared(&code_records);
    }
    if(signature)(*ti)->Deallocate(ti,(unsigned char*)signature);if(loader)native_original.DeleteLocalRef(env,loader);
    NativeThread *state=candidate?native_thread():NULL;if(!state)return;jclass previous=state->prepared;state->prepared=type;state->control++;
    native_original.CallStaticVoidMethod(env,native_controller,code_prepared_callback,type);state->control--;state->prepared=previous;
    if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_preparedClass0(JNIEnv *env,jclass controller,jclass type){
    (void)controller;NativeThread *state=native_thread();return state&&state->prepared&&code_caller_gate
            &&native_original.CallStaticBooleanMethod(env,native_controller,code_caller_gate)&&native_original.IsSameObject(env,type,state->prepared);
}
/* A result is used only after actual frame bytecodes and referenced constants match this version. */
static int code_frame_sources(JNIEnv *env,jmethodID method,jlocation location,OwnerLink **owners,int *known){
    if(!code_available||location<0)return 1;jclass declaring=NULL;jobject loader=NULL;char *signature=NULL,*name=NULL,*descriptor=NULL;unsigned char *bytes=NULL;jint length=0;CodePool pool={0};CodeVMMethod version={0};int valid=1;
    if((*native_ti)->GetMethodDeclaringClass(native_ti,method,&declaring)!=JVMTI_ERROR_NONE||(*native_ti)->GetClassLoader(native_ti,declaring,&loader)!=JVMTI_ERROR_NONE||(*native_ti)->GetClassSignature(native_ti,declaring,&signature,NULL)!=JVMTI_ERROR_NONE||(*native_ti)->GetMethodName(native_ti,method,&name,&descriptor,NULL)!=JVMTI_ERROR_NONE){valid=0;goto done;}
    AcquireSRWLockShared(&code_records);CodeImage *images=code_images;int candidate=0;
    for(CodeImage *image=images;image&&!candidate;image=image->next)if(code_class_matches(env,image,declaring,loader,signature)){
        CodeMethod *entry=code_method(image,name,descriptor);if(entry&&entry->owners)candidate=1;
    }ReleaseSRWLockShared(&code_records);
    if(!candidate)goto done;*known=1;
    if(!code_vm_snapshot(env,method,&version,&pool,name,descriptor)||(*native_ti)->GetBytecodes(native_ti,method,&length,&bytes)!=JVMTI_ERROR_NONE){valid=0;goto done;}
    valid=0;AcquireSRWLockShared(&code_records);
    for(CodeImage *image=images;image&&!valid;image=image->next)if(code_class_matches(env,image,declaring,loader,signature)){
        CodeMethod *entry=code_method(image,name,descriptor);if(!entry||!entry->owners)continue;
        unsigned ordinal=0;if(code_equivalent(image,entry,bytes,length,&pool,&version,location,&ordinal)&&code_vm_unchanged(method,&version))valid=native_owner_merge(owners,entry->owners[ordinal]);
    }ReleaseSRWLockShared(&code_records);
done:
    free(version.handlers);code_pool_free(&pool);if(bytes)(*native_ti)->Deallocate(native_ti,bytes);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);if(loader)native_original.DeleteLocalRef(env,loader);if(declaring)native_original.DeleteLocalRef(env,declaring);return valid;
}
static int code_trace(jthread thread,jvmtiFrameInfo **frames,jint *count){
    jint depth=0;if((*native_ti)->GetFrameCount(native_ti,thread,&depth)!=JVMTI_ERROR_NONE)return 0;
    *frames=(jvmtiFrameInfo*)calloc((size_t)depth+1,sizeof(**frames));return *frames&&(*native_ti)->GetStackTrace(native_ti,thread,0,depth,*frames,count)==JVMTI_ERROR_NONE;
}
static jobject code_frame_plan(JNIEnv *env,jmethodID method,jlocation location){
    if(!code_available||location<0)return NULL;
    jclass declaring=NULL;jobject loader=NULL,result=NULL;char *signature=NULL,*name=NULL,*descriptor=NULL;
    unsigned char *bytes=NULL;jint length=0,access=0;CodePool pool={0};CodeVMMethod version={0};
    if((*native_ti)->GetMethodDeclaringClass(native_ti,method,&declaring)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetClassLoader(native_ti,declaring,&loader)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetClassSignature(native_ti,declaring,&signature,NULL)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetMethodName(native_ti,method,&name,&descriptor,NULL)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetMethodModifiers(native_ti,method,&access)!=JVMTI_ERROR_NONE)goto done;
    int candidate=0;AcquireSRWLockShared(&code_records);
    for(CodeImage *image=code_images;image&&!candidate;image=image->next)
        if(image->execution&&!native_original.IsSameObject(env,image->execution,NULL)&&code_class_matches(env,image,declaring,loader,signature))candidate=1;
    ReleaseSRWLockShared(&code_records);if(!candidate)goto done;
    if(!code_vm_snapshot(env,method,&version,&pool,name,descriptor)||(*native_ti)->GetBytecodes(native_ti,method,&length,&bytes)!=JVMTI_ERROR_NONE)goto done;
    AcquireSRWLockShared(&code_records);
    for(CodeImage *image=code_images;image&&!result;image=image->next){
        if(!image->execution||!code_class_matches(env,image,declaring,loader,signature))continue;
        CodeMethod *entry=code_method(image,name,descriptor);if(!entry||entry->access!=(unsigned)access)continue;
        unsigned ordinal=0;
        if(code_equivalent(image,entry,bytes,length,&pool,&version,location,&ordinal)&&code_vm_unchanged(method,&version))
            result=native_original.NewLocalRef(env,image->execution);
    }
    ReleaseSRWLockShared(&code_records);
done:
    free(version.handlers);code_pool_free(&pool);if(bytes)(*native_ti)->Deallocate(native_ti,bytes);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
    if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);if(loader)native_original.DeleteLocalRef(env,loader);
    if(declaring)native_original.DeleteLocalRef(env,declaring);return result;
}
static jclass code_field_owner(JNIEnv *env,jclass type,const char *name,const char *descriptor,int *ready){
    if(!type||!*ready)return NULL;
    jfieldID *fields=NULL;jint count=0;jclass result=NULL;
    if((*native_ti)->GetClassFields(native_ti,type,&count,&fields)!=JVMTI_ERROR_NONE){*ready=0;return NULL;}
    for(jint i=0;i<count&&!result;i++){
        char *actual=NULL,*signature=NULL;
        if((*native_ti)->GetFieldName(native_ti,type,fields[i],&actual,&signature,NULL)!=JVMTI_ERROR_NONE)*ready=0;
        else if(!strcmp(actual,name)&&!strcmp(signature,descriptor)){result=(jclass)native_original.NewLocalRef(env,type);if(!result)*ready=0;}
        if(actual)(*native_ti)->Deallocate(native_ti,(unsigned char*)actual);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
        if(!*ready)break;
    }
    if(fields)(*native_ti)->Deallocate(native_ti,(unsigned char*)fields);if(result||!*ready)return result;
    jclass *interfaces=NULL;count=0;
    if((*native_ti)->GetImplementedInterfaces(native_ti,type,&count,&interfaces)!=JVMTI_ERROR_NONE){*ready=0;return NULL;}
    for(jint i=0;i<count;i++){
        if(!result&&*ready)result=code_field_owner(env,interfaces[i],name,descriptor,ready);
        native_original.DeleteLocalRef(env,interfaces[i]);
    }
    if(interfaces)(*native_ti)->Deallocate(native_ti,(unsigned char*)interfaces);if(result||!*ready)return result;
    jclass parent=native_original.GetSuperclass(env,type);
    if(parent){result=code_field_owner(env,parent,name,descriptor,ready);native_original.DeleteLocalRef(env,parent);}
    return result;
}
JNIEXPORT jclass JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_codeFieldOwner0(JNIEnv *env,jclass controller,jclass symbolic,jstring name,jstring descriptor){
    (void)controller;if(!native_jni_ready||!code_caller_gate||!native_original.CallStaticBooleanMethod(env,native_controller,code_caller_gate)){
        native_refuse(env,"ACTUAL_CODE_FIELD_BRIDGE_REQUIRED");return NULL;
    }
    if(!native_ti||!symbolic||!name||!descriptor)return NULL;
    const char *key=native_original.GetStringUTFChars(env,name,NULL),*desc=key?native_original.GetStringUTFChars(env,descriptor,NULL):NULL;
    int ready=key&&desc;jclass result=ready?code_field_owner(env,symbolic,key,desc,&ready):NULL;
    if(key)native_original.ReleaseStringUTFChars(env,name,key);if(desc)native_original.ReleaseStringUTFChars(env,descriptor,desc);
    if(!ready&&result){native_original.DeleteLocalRef(env,result);result=NULL;}return result;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_executionRootAllowed0(JNIEnv *env,jclass type){
    if(!native_jni_ready||!code_root_gate||!native_original.IsSameObject(env,type,native_controller))return JNI_FALSE;
    NativeThread *state=native_thread();if(!state||state->control)return JNI_FALSE;
    // The existing native control scope is allocation-free and belongs to this
    // real thread. Recursive Object construction during the callback returns here.
    state->control++;
    jboolean result=native_original.CallStaticBooleanMethod(env,native_controller,code_root_gate);
    state->control--;return native_original.ExceptionCheck(env)?JNI_FALSE:result;
}
JNIEXPORT jobject JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_executionPlan0(JNIEnv *env,jclass type,jclass declaring,jstring name,jstring descriptor,jint location){
    (void)type;if(!native_jni_ready||!code_caller_gate||!native_original.CallStaticBooleanMethod(env,native_controller,code_caller_gate)){
        native_refuse(env,"ACTUAL_CODE_FRAME_BRIDGE_REQUIRED");return NULL;
    }
    if(!code_available||!declaring||!name||!descriptor||location<0)return NULL;
    jobject result=NULL;jthread thread=NULL;jvmtiFrameInfo *frames=NULL;jint count=0;
    const char *key=native_original.GetStringUTFChars(env,name,NULL),*desc=key?native_original.GetStringUTFChars(env,descriptor,NULL):NULL;
    if(key&&desc&&(*native_ti)->GetCurrentThread(native_ti,&thread)==JVMTI_ERROR_NONE&&code_trace(thread,&frames,&count)){
        for(jint i=0;i<count;i++){
            if(frames[i].location!=location)continue;
            jclass actual=NULL;char *method=NULL,*signature=NULL;
            int same=(*native_ti)->GetMethodDeclaringClass(native_ti,frames[i].method,&actual)==JVMTI_ERROR_NONE
                    &&native_original.IsSameObject(env,actual,declaring)
                    &&(*native_ti)->GetMethodName(native_ti,frames[i].method,&method,&signature,NULL)==JVMTI_ERROR_NONE
                    &&!strcmp(method,key)&&!strcmp(signature,desc);
            if(actual)native_original.DeleteLocalRef(env,actual);if(method)(*native_ti)->Deallocate(native_ti,(unsigned char*)method);
            if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
            if(same){result=code_frame_plan(env,frames[i].method,frames[i].location);break;}
        }
    }
    if(key)native_original.ReleaseStringUTFChars(env,name,key);if(desc)native_original.ReleaseStringUTFChars(env,descriptor,desc);
    free(frames);if(thread)native_original.DeleteLocalRef(env,thread);return result;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_executionWatch0(JNIEnv *env,jclass controller,jclass declaring,jstring name,jstring descriptor,jint location,jobject plan,jobject token){
    (void)controller;if(!native_jni_ready||!code_caller_gate||!native_original.CallStaticBooleanMethod(env,native_controller,code_caller_gate)){
        native_refuse(env,"ACTUAL_CODE_FRAME_BRIDGE_REQUIRED");return JNI_FALSE;
    }
    NativeThread *state=native_thread();jboolean result=JNI_FALSE;
    if(!state||!code_pop_available||!code_pop_callback||!declaring||!name||!descriptor||location<0||!plan||!token)goto failed;
    jthread thread=NULL;jvmtiFrameInfo *frames=NULL;jint count=0;
    const char *key=native_original.GetStringUTFChars(env,name,NULL),*desc=key?native_original.GetStringUTFChars(env,descriptor,NULL):NULL;
    if(key&&desc&&(*native_ti)->GetCurrentThread(native_ti,&thread)==JVMTI_ERROR_NONE&&code_trace(thread,&frames,&count)){
        for(jint i=0;i<count;i++){
            if(frames[i].location!=location)continue;
            jclass actual=NULL;char *method=NULL,*signature=NULL;
            int same=(*native_ti)->GetMethodDeclaringClass(native_ti,frames[i].method,&actual)==JVMTI_ERROR_NONE
                    &&native_original.IsSameObject(env,actual,declaring)
                    &&(*native_ti)->GetMethodName(native_ti,frames[i].method,&method,&signature,NULL)==JVMTI_ERROR_NONE
                    &&!strcmp(method,key)&&!strcmp(signature,desc);
            if(actual)native_original.DeleteLocalRef(env,actual);if(method)(*native_ti)->Deallocate(native_ti,(unsigned char*)method);
            if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(!same)continue;
            jobject recorded=code_frame_plan(env,frames[i].method,frames[i].location);
            int matched=recorded&&native_original.IsSameObject(env,recorded,plan);if(recorded)native_original.DeleteLocalRef(env,recorded);
            if(!matched)break;
            CodeExit *entry=(CodeExit*)calloc(1,sizeof(*entry));if(!entry)break;
            entry->method=frames[i].method;entry->token=native_original.NewGlobalRef(env,token);
            if(entry->token&&(*native_ti)->NotifyFramePop(native_ti,thread,i)==JVMTI_ERROR_NONE){entry->previous=state->execution;state->execution=entry;result=JNI_TRUE;}
            else{if(entry->token)native_original.DeleteGlobalRef(env,entry->token);free(entry);}
            break;
        }
    }
    if(key)native_original.ReleaseStringUTFChars(env,name,key);if(desc)native_original.ReleaseStringUTFChars(env,descriptor,desc);
    free(frames);if(thread)native_original.DeleteLocalRef(env,thread);
failed:
    if(!result)InterlockedIncrement(&code_pop_failures);return result;
}
static void code_exit_dispatch(JNIEnv *env,NativeThread *state,CodeExit *entry,jboolean normal){
    jthrowable pending=native_original.ExceptionOccurred(env);if(pending)native_original.ExceptionClear(env);
    jobject previous=state->popped;state->popped=entry->token;state->control++;
    jboolean closed=native_original.CallStaticBooleanMethod(env,native_controller,code_pop_callback,entry->token,normal);
    state->control--;state->popped=previous;
    if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&code_pop_failures);}
    else if(!closed)InterlockedIncrement(&code_pop_failures);
    if(pending){native_original.Throw(env,pending);native_original.DeleteLocalRef(env,pending);}
    native_original.DeleteGlobalRef(env,entry->token);SecureZeroMemory(entry,sizeof(*entry));free(entry);
}
static void JNICALL code_frame_popped(jvmtiEnv *ti,JNIEnv *env,jthread thread,jmethodID method,jboolean exceptional){
    (void)ti;(void)thread;if(!native_jni_ready||!code_pop_callback||native_tls==TLS_OUT_OF_INDEXES)return;
    NativeThread *state=(NativeThread*)TlsGetValue(native_tls);if(!state||!state->execution)return;
    CodeExit *entry=state->execution;if(entry->method!=method){InterlockedIncrement(&code_pop_failures);return;}
    state->execution=entry->previous;code_exit_dispatch(env,state,entry,exceptional?JNI_FALSE:JNI_TRUE);
}
static void code_thread_end(JNIEnv *env,NativeThread *state){
    while(state->execution){CodeExit *entry=state->execution;state->execution=entry->previous;
        InterlockedIncrement(&code_pop_failures);code_exit_dispatch(env,state,entry,JNI_FALSE);
    }
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_executionPopPermit0(JNIEnv *env,jclass controller,jobject token){
    (void)controller;NativeThread *state=native_thread();
    return state&&state->popped&&code_caller_gate&&native_original.CallStaticBooleanMethod(env,native_controller,code_caller_gate)
            &&native_original.IsSameObject(env,state->popped,token)?JNI_TRUE:JNI_FALSE;
}
static int code_current_sources(JNIEnv *env,OwnerLink **owners){
    NativeThread *state=native_thread();if(!native_jni_ready||!state||state->control)return 1;
    if(!native_capture_network_sources(env,state,owners))return 0;
    if(!code_available)return 1;
    // With no published instruction owner, every frame relevance predicate
    // is false. Actual Java scope/native binding/library capture still runs;
    // plans, bytecode validation and heap observations do not use this shortcut.
    if(!InterlockedCompareExchange(&code_sourced,0,0))return 1;
    state->control++;jthread thread=NULL;jvmtiFrameInfo *frames=NULL;jint depth=0;int valid=1;
    if((*native_ti)->GetCurrentThread(native_ti,&thread)!=JVMTI_ERROR_NONE||!code_trace(thread,&frames,&depth))valid=0;
    else for(jint i=0;i<depth&&valid;i++){
        if(frames[i].location<0||!code_method_relevant(env,frames[i].method,NULL))continue;
        int known=0;valid=code_frame_sources(env,frames[i].method,frames[i].location,owners,&known);
    }
    free(frames);if(thread)native_original.DeleteLocalRef(env,thread);state->control--;return valid;
}
static int code_current_stopped(JNIEnv *env){
    OwnerLink *owners=NULL;int observed=code_current_sources(env,&owners),stopped=native_owner_stopped(owners)!=NULL;
    code_links_free(owners);if(!observed){native_refuse(env,"EXTERNAL_NATIVE_CALL_SOURCE_UNOBSERVED");return 1;}return stopped;
}
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_codeFrame0(JNIEnv *env,jclass type,jclass declaring,jstring name,jstring descriptor,jint location){
    (void)type;if(!native_jni_ready||!code_caller_gate||!native_original.CallStaticBooleanMethod(env,native_controller,code_caller_gate)){native_refuse(env,"ACTUAL_CODE_FRAME_BRIDGE_REQUIRED");return NULL;}
    OwnerLink *owners=NULL;jthread thread=NULL;jvmtiFrameInfo *frames=NULL;jint count=0;const char *key=native_original.GetStringUTFChars(env,name,NULL),*desc=native_original.GetStringUTFChars(env,descriptor,NULL);
    if(code_available&&key&&desc&&(*native_ti)->GetCurrentThread(native_ti,&thread)==JVMTI_ERROR_NONE&&code_trace(thread,&frames,&count))for(jint i=0;i<count;i++){
        if(frames[i].location!=location)continue;jclass actual=NULL;char *method=NULL,*signature=NULL;
        int matches=(*native_ti)->GetMethodDeclaringClass(native_ti,frames[i].method,&actual)==JVMTI_ERROR_NONE&&native_original.IsSameObject(env,actual,declaring)
                &&(*native_ti)->GetMethodName(native_ti,frames[i].method,&method,&signature,NULL)==JVMTI_ERROR_NONE&&!strcmp(method,key)&&!strcmp(signature,desc);
        if(method)(*native_ti)->Deallocate(native_ti,(unsigned char*)method);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(actual)native_original.DeleteLocalRef(env,actual);
        if(matches){int known=0;if(!code_frame_sources(env,frames[i].method,frames[i].location,&owners,&known)){code_links_free(owners);owners=NULL;}break;}
    }
    if(key)native_original.ReleaseStringUTFChars(env,name,key);if(desc)native_original.ReleaseStringUTFChars(env,descriptor,desc);free(frames);if(thread)native_original.DeleteLocalRef(env,thread);
    jclass module=native_original.FindClass(env,"java/lang/Module");unsigned size=0;for(OwnerLink *entry=owners;entry;entry=entry->next)if(!native_original.IsSameObject(env,entry->owner->module,NULL))size++;
    jobjectArray result=module?native_original.NewObjectArray(env,size,module,NULL):NULL;unsigned at=0;for(OwnerLink *entry=owners;result&&entry;entry=entry->next)if(!native_original.IsSameObject(env,entry->owner->module,NULL))native_original.SetObjectArrayElement(env,result,at++,entry->owner->module);
    if(module)native_original.DeleteLocalRef(env,module);code_links_free(owners);return result;
}
