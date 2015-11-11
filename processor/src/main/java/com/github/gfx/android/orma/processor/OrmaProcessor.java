package com.github.gfx.android.orma.processor;

import com.github.gfx.android.orma.annotation.Table;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({
        "com.github.gfx.android.orma.annotation.*",
        "com.google.gson.annotations.SerializedName"})
public class OrmaProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        DatabaseWriter databaseWriter = new DatabaseWriter(processingEnv);

        buildSchemas(roundEnv)
                .peek(this::writeSchema)
                .peek(this::writeRelation)
                .forEach(databaseWriter::add);

        if (databaseWriter.isRequired()) {
            writeToFiler(null,
                    JavaFile.builder(databaseWriter.getPackageName(),
                            databaseWriter.buildTypeSpec())
                            .build());
        }

        return false;
    }

    public Stream<SchemaDefinition> buildSchemas(RoundEnvironment roundEnv) {
        SchemaValidator validator = new SchemaValidator();
        return roundEnv
                .getElementsAnnotatedWith(Table.class)
                .stream()
                .map(element -> new SchemaDefinition(validator.validate(element)));
    }

    public void writeSchema(SchemaDefinition schema) {
        SchemaWriter writer = new SchemaWriter(schema, processingEnv);
        writeToFilerForEachModel(schema, writer.buildTypeSpec());
    }

    public void writeRelation(SchemaDefinition schema) {
        RelationWriter writer = new RelationWriter(schema, processingEnv);
        writeToFilerForEachModel(schema, writer.buildTypeSpec());
    }

    public void writeToFilerForEachModel(SchemaDefinition schema, TypeSpec typeSpec) {
        writeToFiler(schema.getElement(),
                JavaFile.builder(schema.getPackageName(), typeSpec)
                        .build());
    }

    public void writeToFiler(Element element, JavaFile javaFile) {
        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            error("Failed to write " + javaFile.typeSpec.name + ": " + e, element);
        }
    }

    void note(CharSequence message, Element element) {
        printMessage(Diagnostic.Kind.NOTE, message, element);
    }

    void error(CharSequence message, Element element) {
        printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    void printMessage(Diagnostic.Kind kind, CharSequence message, Element element) {
        processingEnv.getMessager().printMessage(kind, message, element);
    }
}