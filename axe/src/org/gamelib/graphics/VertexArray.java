/**
 * 
 */
package org.gamelib.graphics;

import java.nio.FloatBuffer;

import org.gamelib.graphics.VertexAttrib.Type;
import org.lwjgl.BufferUtils;

/** @author pwnedary */
public class VertexArray implements VertexData {
	private GL10 gl;
	private final VertexAttributes attributes;
	private final FloatBuffer buffer;

	public VertexArray(GL10 gl, int numVertices, VertexAttrib... attributes) {
		this.gl = gl;
		this.attributes = new VertexAttributes(attributes);
		buffer = (FloatBuffer) BufferUtils.createFloatBuffer(this.attributes.vertexSize / 4 * numVertices).flip();
	}

	public void bind() {
		for (int i = 0, textureUnit = 0; i < attributes.size(); i++) {
			VertexAttrib attrib = attributes.get(i);
			buffer.position(attrib.location / 4);

			switch (attrib.type) {
			case POSITION:
				gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
				gl.glVertexPointer(attrib.numComponents, GL10.GL_FLOAT, attributes.vertexSize, buffer);
				break;
			case COLOR:
			case COLOR_PACKED:
				gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
				gl.glColorPointer(attrib.numComponents, attrib.type == Type.COLOR ? GL10.GL_FLOAT : GL10.GL_UNSIGNED_BYTE, attributes.vertexSize, buffer);
				break;
			case NORMAL:
				gl.glEnableClientState(GL10.GL_NORMAL_ARRAY);
				gl.glNormalPointer(GL10.GL_FLOAT, attributes.vertexSize, buffer);
				break;
			case TEXTURE_COORDINATES:
				gl.glClientActiveTexture(GL10.GL_TEXTURE0 + textureUnit++);
				gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
				gl.glTexCoordPointer(attrib.numComponents, GL10.GL_FLOAT, attributes.vertexSize, buffer);
				break;
			}
		}
	}

	public void unbind() {
		for (int i = 0, textureUnit = 0; i < attributes.size(); i++) {
			VertexAttrib attrib = attributes.get(i);

			switch (attrib.type) {
			case POSITION:
				// gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
				break;
			case COLOR:
			case COLOR_PACKED:
				gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
				break;
			case NORMAL:
				gl.glDisableClientState(GL10.GL_NORMAL_ARRAY);
				break;
			case TEXTURE_COORDINATES:
				gl.glClientActiveTexture(GL10.GL_TEXTURE0 + textureUnit++);
				gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
				break;
			}
		}
		buffer.position(0);
	}

	@Override
	public void setVertices(float[] vertices, int offset, int length) {
		((FloatBuffer) buffer.clear()).put(vertices, offset, length).flip();
	}

	@Override
	public int getNumVertices() {
		return buffer.limit() * 4 / attributes.vertexSize;
	}
}