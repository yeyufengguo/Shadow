package com.tencent.shadow.core.transform.specific

import com.tencent.shadow.core.transform_kit.CodeConverterExtension
import com.tencent.shadow.core.transform_kit.SpecificTransform
import com.tencent.shadow.core.transform_kit.TransformStep
import javassist.ClassPool
import javassist.CodeConverter
import javassist.CtClass
import javassist.bytecode.Descriptor

class ContentProviderTransform : SpecificTransform() {
    companion object {
        const val ShadowUriClassname = "com.tencent.shadow.runtime.UriConverter"
        const val AndroidUriClassname = "android.net.Uri"
        const val uriBuilderName = "android.net.Uri\$Builder"
        const val resolverName = "android.content.ContentResolver"
    }

    private fun prepareUriParseCodeConverter(classPool: ClassPool): CodeConverter {
        val uriMethod = mClassPool[AndroidUriClassname].methods!!
        val shadowUriMethod = mClassPool[ShadowUriClassname].methods!!

        val method_parse = uriMethod.filter { it.name == "parse" }
        val shadow_method_parse = shadowUriMethod.filter { it.name == "parse" }!!
        val codeConverter = CodeConverter()

        for (ctAndroidMethod in method_parse) {
            for (ctShadowMedthod in shadow_method_parse) {
                if (ctAndroidMethod.methodInfo.descriptor == ctShadowMedthod.methodInfo.descriptor) {
                    codeConverter.redirectMethodCall(ctAndroidMethod, ctShadowMedthod)
                }
            }
        }
        return codeConverter
    }

    private fun prepareUriBuilderCodeConverter(classPool: ClassPool): CodeConverter {
        val uriClass = mClassPool[AndroidUriClassname]
        val uriBuilderClass = mClassPool[uriBuilderName]
        val buildMethod = uriBuilderClass.getMethod("build", Descriptor.ofMethod(uriClass, null))
        val newBuildMethod = mClassPool[ShadowUriClassname].getMethod("build", Descriptor.ofMethod(uriClass, arrayOf(uriBuilderClass)))
        val codeConverterExt = CodeConverterExtension()
        codeConverterExt.redirectMethodCallToStaticMethodCall(buildMethod, newBuildMethod)
        return codeConverterExt
    }

    private fun prepareContentResolverCodeConverter(classPool: ClassPool): CodeConverter {
        val codeConverter = CodeConverterExtension()
        val resolverClass = classPool[resolverName]
        val targetClass = classPool[ShadowUriClassname]
        val uriClass = classPool["android.net.Uri"]
        val stringClass = classPool["java.lang.String"]
        val bundleClass = classPool["android.os.Bundle"]
        val observerClass = classPool["android.database.ContentObserver"]

        val callMethod = resolverClass.getMethod("call", Descriptor.ofMethod(bundleClass,
                arrayOf(uriClass, stringClass, stringClass, bundleClass)))
        val newCallMethod = targetClass.getMethod("call", Descriptor.ofMethod(bundleClass,
                arrayOf(resolverClass, uriClass, stringClass, stringClass, bundleClass)))
        codeConverter.redirectMethodCallToStaticMethodCall(callMethod, newCallMethod)

        val notifyMethod1 = resolverClass.getMethod("notifyChange", Descriptor.ofMethod(CtClass.voidType,
                arrayOf(uriClass, observerClass)))
        val newNotifyMethod1 = targetClass.getMethod("notifyChange", Descriptor.ofMethod(CtClass.voidType,
                arrayOf(resolverClass, uriClass, observerClass)))
        codeConverter.redirectMethodCallToStaticMethodCall(notifyMethod1, newNotifyMethod1)

        val notifyMethod2 = resolverClass.getMethod("notifyChange", Descriptor.ofMethod(CtClass.voidType,
                arrayOf(uriClass, observerClass, CtClass.booleanType)))
        val newNotifyMethod2 = targetClass.getMethod("notifyChange", Descriptor.ofMethod(CtClass.voidType,
                arrayOf(resolverClass, uriClass, observerClass, CtClass.booleanType)))
        codeConverter.redirectMethodCallToStaticMethodCall(notifyMethod2, newNotifyMethod2)

        val notifyMethod3 = resolverClass.getMethod("notifyChange", Descriptor.ofMethod(CtClass.voidType,
                arrayOf(uriClass, observerClass, CtClass.intType)))
        val newNotifyMethod3 = targetClass.getMethod("notifyChange", Descriptor.ofMethod(CtClass.voidType,
                arrayOf(resolverClass, uriClass, observerClass, CtClass.intType)))
        codeConverter.redirectMethodCallToStaticMethodCall(notifyMethod3, newNotifyMethod3)

        return codeConverter
    }

    override fun setup(allInputClass: Set<CtClass>) {

        val uriParseCodeConverter = prepareUriParseCodeConverter(mClassPool)
        val uriBuilderCodeConverter = prepareUriBuilderCodeConverter(mClassPool)
        val contentResolverCodeConverter = prepareContentResolverCodeConverter(mClassPool)

        newStep(object : TransformStep {
            override fun filter(allInputClass: Set<CtClass>) =
                    allCanRecompileAppClass(allInputClass, listOf(AndroidUriClassname))

            override fun transform(ctClass: CtClass) {
                try {
                    ctClass.instrument(uriParseCodeConverter)
                } catch (e: Exception) {
                    System.err.println("处理" + ctClass.name + "时出错")
                    throw e
                }
            }
        })

        newStep(object : TransformStep {
            override fun filter(allInputClass: Set<CtClass>) =
                    allCanRecompileAppClass(allInputClass, listOf(uriBuilderName))

            override fun transform(ctClass: CtClass) {
                try {
                    ctClass.instrument(uriBuilderCodeConverter)
                } catch (e: Exception) {
                    System.err.println("处理" + ctClass.name + "时出错")
                    throw e
                }
            }
        })

        newStep(object : TransformStep {
            override fun filter(allInputClass: Set<CtClass>) =
                    allCanRecompileAppClass(allInputClass, listOf(resolverName))

            override fun transform(ctClass: CtClass) {
                try {
                    ctClass.instrument(contentResolverCodeConverter)
                } catch (e: Exception) {
                    System.err.println("处理" + ctClass.name + "时出错")
                    throw e
                }
            }
        })
    }
}